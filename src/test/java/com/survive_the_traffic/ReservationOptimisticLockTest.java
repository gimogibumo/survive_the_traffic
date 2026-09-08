package com.survive_the_traffic;

import static org.assertj.core.api.Assertions.assertThat;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.concert.repository.ConcertRepository;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.domain.SeatStatus;
import com.survive_the_traffic.seat.repository.SeatRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ReservationOptimisticLockTest {

    private final SeatRepository seatRepository;
    private final ConcertRepository concertRepository;
    private final PlatformTransactionManager transactionManager;

    private Long seatId;

    @Autowired
    ReservationOptimisticLockTest(
        SeatRepository seatRepository,
        ConcertRepository concertRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.seatRepository = seatRepository;
        this.concertRepository = concertRepository;
        this.transactionManager = transactionManager;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();

        Concert concert = concertRepository.save(
            Concert.builder()
                .concertAt(LocalDateTime.of(2026, 6, 30, 19, 0))
                .title("Optimistic Test " + suffix)
                .reservationOpenAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                .createdAt(LocalDateTime.now())
                .build()
        );

        Seat seat = seatRepository.save(
            Seat.builder()
                .section("A")
                .rowNumber(1)
                .seatNumber(1)
                .price(10000L)
                .status(SeatStatus.AVAILABLE)
                .concert(concert)
                .build()
        );

        seatId = seat.getId();
    }

    @Test
    void version이_같은_좌석을_동시에_수정하면_optimistic_lock_충돌이_발생한다()
        throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier bothTransactionsLoadedSeat = new CyclicBarrier(2);

        try {
            Future<?> first = executor.submit(() ->
                holdSeatWithoutExplicitLock(bothTransactionsLoadedSeat)
            );
            Future<?> second = executor.submit(() ->
                holdSeatWithoutExplicitLock(bothTransactionsLoadedSeat)
            );

            Throwable firstFailure = failureOf(first);
            Throwable secondFailure = failureOf(second);

            assertThat(exactlyOneFailedWithOptimisticLock(
                firstFailure,
                secondFailure
            )).isTrue();

            Seat seat = seatRepository.findById(seatId).orElseThrow();
            assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(seat.getVersion()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private void holdSeatWithoutExplicitLock(CyclicBarrier barrier) {
        TransactionTemplate transactionTemplate =
            new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            Seat seat = seatRepository.findById(seatId).orElseThrow();
            await(barrier);
            seat.hold();
            seatRepository.flush();
        });
    }

    private Throwable failureOf(Future<?> future)
        throws InterruptedException {

        try {
            future.get(10, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            return e;
        }
    }

    private boolean exactlyOneFailedWithOptimisticLock(
        Throwable first,
        Throwable second
    ) {
        return first == null
            && containsOptimisticLockFailure(second)
            || second == null
            && containsOptimisticLockFailure(first);
    }

    private boolean containsOptimisticLockFailure(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            String className = current.getClass().getName();

            if (className.contains("Optimistic")
                || className.contains("StaleObjectState")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
