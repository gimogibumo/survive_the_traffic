package com.survive_the_traffic;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.concert.repository.ConcertRepository;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.domain.SeatStatus;
import com.survive_the_traffic.seat.repository.SeatRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.SQLException;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseLockTest {

    @Autowired
    private LockExperimentService lockExperimentService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ConcertRepository concertRepository;

    private boolean containsMessage(
        Throwable throwable,
        String target
    ) {
        Throwable current = throwable;

        while (current != null) {

            String message = current.getMessage();

            if (message != null
                && message.toLowerCase()
                .contains(target.toLowerCase())) {

                return true;
            }

            // 원인 예외를 계속 따라감
            current = current.getCause();
        }

        return false;
    }


    private void printExceptionChain(
        Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {

            System.out.println(
                current.getClass().getName()
                    + " : "
                    + current.getMessage()
            );

            // JDBC 예외라면 SQLState도 출력
            if (current instanceof SQLException sqlException) {
                System.out.println(
                    "SQLState = "
                        + sqlException.getSQLState()
                );
            }

            current = current.getCause();
        }
    }


    @Test
    void pessimistic_lock_timeout_테스트() throws Exception {

        Concert concert = saveConcert("Lock Timeout");
        Seat seat = saveSeat(concert, 1);
        Long seatId = seat.getId();

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        /*
         * Tx A가 실제로 lock을 잡았다는 것을
         * 확인하기 위한 latch.
         */
        CountDownLatch lockAcquiredLatch =
            new CountDownLatch(1);

        try {

            /*
             * ------------------------------------------------
             * Transaction A
             *
             * Seat lock 획득
             * ↓
             * 3초 동안 lock 유지
             * ------------------------------------------------
             */
            Future<?> holder =
                executor.submit(() -> {

                    lockExperimentService.holdLock(
                        seatId,
                        lockAcquiredLatch,
                        3000
                    );
                });


            /*
             * Tx A가 진짜 lock을 얻을 때까지 기다린다.
             *
             * 이게 핵심이다.
             *
             * 그냥 Thread.sleep(500)으로 추측하면
             * 환경에 따라 Tx A가 아직 lock을 못 얻었을 수도 있다.
             */
            boolean acquired =
                lockAcquiredLatch.await(
                    2,
                    TimeUnit.SECONDS
                );

            assertThat(acquired)
                .isTrue();


            /*
             * ------------------------------------------------
             * Transaction B
             *
             * lock_timeout = 1초
             *
             * 그런데 Tx A는 3초간 lock 보유.
             *
             * 따라서 B는 실패해야 한다.
             * ------------------------------------------------
             */

            long start =
                System.currentTimeMillis();

            Future<?> contender =
                executor.submit(() -> {

                    lockExperimentService
                        .tryLockWithTimeout(
                            seatId,
                            1000
                        );
                });

            Throwable failure = null;

            try {

                contender.get(
                    5,
                    TimeUnit.SECONDS
                );

            } catch (ExecutionException e) {

                failure = e.getCause();
            }

            long elapsed =
                System.currentTimeMillis()
                    - start;

            System.out.println(
                "Lock wait elapsed = "
                    + elapsed
                    + "ms"
            );

            printExceptionChain(failure);


            /*
             * timeout이 반드시 발생해야 한다.
             */
            assertThat(failure)
                .isNotNull();


            /*
             * PostgreSQL 내부 원인 메시지에
             * lock timeout이 있는지 확인.
             *
             * Spring/Hibernate 버전에 따라
             * 최상위 Exception 클래스가 달라질 수 있어서
             * 처음에는 root cause를 보는 것이 좋다.
             */
            assertThat(
                containsMessage(
                    failure,
                    "lock timeout"
                )
            ).isTrue();


            /*
             * Holder transaction도 정상 종료됐는지 확인.
             */
            holder.get(
                5,
                TimeUnit.SECONDS
            );

        } finally {

            executor.shutdownNow();
        }
    }

    @Test
    void 서로_반대_순서로_lock을_잡으면_deadlock이_발생한다()
        throws Exception {

        Concert concert = saveConcert("Deadlock");
        Seat seat1 = saveSeat(concert, 1);
        Seat seat2 = saveSeat(concert, 2);

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        CyclicBarrier bothTransactionsLockedFirstSeat =
            new CyclicBarrier(2);

        try {
            Future<?> first =
                executor.submit(() ->
                    lockExperimentService.lockTwoSeatsForDeadlock(
                        seat1.getId(),
                        seat2.getId(),
                        bothTransactionsLockedFirstSeat
                    )
                );

            Future<?> second =
                executor.submit(() ->
                    lockExperimentService.lockTwoSeatsForDeadlock(
                        seat2.getId(),
                        seat1.getId(),
                        bothTransactionsLockedFirstSeat
                    )
                );

            Throwable firstFailure = failureOf(first);
            Throwable secondFailure = failureOf(second);

            assertThat(
                containsMessage(firstFailure, "deadlock")
                    || containsMessage(secondFailure, "deadlock")
            ).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 항상_같은_순서로_lock을_잡으면_deadlock이_발생하지_않는다()
        throws Exception {

        Concert concert = saveConcert("Ordered Lock");
        Seat seat1 = saveSeat(concert, 1);
        Seat seat2 = saveSeat(concert, 2);

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        try {
            Future<?> first =
                executor.submit(() ->
                    lockExperimentService.lockTwoSeatsInOrder(
                        seat1.getId(),
                        seat2.getId()
                    )
                );

            Future<?> second =
                executor.submit(() ->
                    lockExperimentService.lockTwoSeatsInOrder(
                        seat2.getId(),
                        seat1.getId()
                    )
                );

            assertThat(failureOf(first)).isNull();
            assertThat(failureOf(second)).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable failureOf(Future<?> future)
        throws InterruptedException {

        try {
            future.get(
                10,
                TimeUnit.SECONDS
            );

            return null;
        } catch (ExecutionException e) {

            return e.getCause();
        } catch (TimeoutException e) {

            future.cancel(true);

            return e;
        }
    }

    private Concert saveConcert(String title) {
        return concertRepository.save(
            Concert.builder()
                .reservationOpenAt(LocalDateTime.now())
                .concertAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .title(title + " " + UUID.randomUUID())
                .build()
        );
    }

    private Seat saveSeat(Concert concert, int seatNumber) {
        return seatRepository.save(
            Seat.builder()
                .concert(concert)
                .status(SeatStatus.AVAILABLE)
                .price(10000L)
                .seatNumber(seatNumber)
                .rowNumber(1)
                .section("A")
                .build()
        );
    }
}
