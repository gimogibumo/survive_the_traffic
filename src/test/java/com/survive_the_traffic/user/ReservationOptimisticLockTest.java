package com.survive_the_traffic.user;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.concert.repository.ConcertRepository;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.repository.ReservationRepository;
import com.survive_the_traffic.reservation.service.ReservationService;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.domain.SeatStatus;
import com.survive_the_traffic.seat.exception.SeatNotAvailableException;
import com.survive_the_traffic.seat.repository.SeatRepository;
import com.survive_the_traffic.user.domain.User;
import com.survive_the_traffic.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest
class ReservationOptimisticLockTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConcertRepository concertRepository;

    private Long seatId;
    private List<Long> userIds;

    @BeforeEach
    void setUp() {

        Concert concert = Concert.builder()
            .concertAt(LocalDateTime.of(2024, 6, 30, 19, 0))
            .title("Test Concert")
            .reservationOpenAt(LocalDateTime.of(2024, 5, 1, 10, 0))
            .createdAt(LocalDateTime.of(2024, 5, 1, 10, 0))
            .build();

        concertRepository.save(concert);

        // 테스트용 좌석 1개 생성
        Seat seat = Seat.builder()
            .section("A")
            .rowNumber(1)
            .seatNumber(1)
            .price(10000L)
            .status(SeatStatus.AVAILABLE)
            .concert(concert)
            .build();

        Seat savedSeat = seatRepository.save(seat);
        seatId = savedSeat.getId();

        // 테스트에 사용할 User 100명 생성
        userIds = new ArrayList<>();

        for (int i = 0; i < 100; i++) {

            User user = User.builder()
                .name("user-" + i)
                .email("user-" + i + "@test.com")
                .createdAt(LocalDateTime.now())
                .build();

            User savedUser = userRepository.save(user);

            userIds.add(savedUser.getId());
        }
    }

    @Test
    void optimistic_lock으로_동시에_같은_좌석을_예약하면_한명만_성공한다()
        throws InterruptedException {

        // given
        int threadCount = 100;

        ExecutorService executorService =
            Executors.newFixedThreadPool(32);

        CountDownLatch startLatch =
            new CountDownLatch(1);

        CountDownLatch doneLatch =
            new CountDownLatch(threadCount);

        AtomicInteger successCount =
            new AtomicInteger();

        AtomicInteger optimisticLockFailureCount =
            new AtomicInteger();

        AtomicInteger seatUnavailableCount =
            new AtomicInteger();

        AtomicInteger unexpectedFailureCount =
            new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {

            Long userId = userIds.get(i);

            executorService.submit(() -> {

                try {

                    // 모든 Thread가 여기까지 도착한 뒤 대기
                    startLatch.await();

                    reservationService.createReservation(
                        new ReservationRequest(userId, seatId)
                    );

                    successCount.incrementAndGet();

                } catch (ObjectOptimisticLockingFailureException e) {

                    optimisticLockFailureCount.incrementAndGet();

                    System.out.println(
                        "[OPTIMISTIC LOCK] "
                            + Thread.currentThread().getName()
                    );

                } catch (SeatNotAvailableException e) {

                    seatUnavailableCount.incrementAndGet();

                } catch (Exception e) {

                    unexpectedFailureCount.incrementAndGet();

                    System.out.println(
                        "[UNEXPECTED] "
                            + e.getClass().getName()
                            + " : "
                            + e.getMessage()
                    );

                } finally {

                    doneLatch.countDown();
                }
            });
        }

        // 100개 task가 거의 동시에 출발
        startLatch.countDown();

        // 모든 task가 끝날 때까지 대기
        doneLatch.await();

        executorService.shutdown();

        // then
        System.out.println("성공: " + successCount.get());
        System.out.println(
            "Optimistic Lock 충돌: "
                + optimisticLockFailureCount.get()
        );
        System.out.println(
            "이미 예약된 좌석: "
                + seatUnavailableCount.get()
        );
        System.out.println(
            "예상하지 못한 실패: "
                + unexpectedFailureCount.get()
        );

        assertThat(successCount.get())
            .isEqualTo(1);

        assertThat(unexpectedFailureCount.get())
            .isEqualTo(0);

        assertThat(
            successCount.get()
                + optimisticLockFailureCount.get()
                + seatUnavailableCount.get()
        ).isEqualTo(threadCount);

        // 최종 DB 상태 확인
        long reservationCount =
            reservationRepository.countBySeatId(seatId);

        assertThat(reservationCount)
            .isEqualTo(1);

        Seat seat = seatRepository.findById(seatId)
            .orElseThrow();

        assertThat(seat.getStatus())
            .isEqualTo(SeatStatus.HELD);
    }
}