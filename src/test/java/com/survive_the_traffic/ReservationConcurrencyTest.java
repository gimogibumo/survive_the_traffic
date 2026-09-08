package com.survive_the_traffic;

import static org.assertj.core.api.Assertions.assertThat;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.concert.repository.ConcertRepository;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReservationConcurrencyTest {

    private static final int THREAD_COUNT = 100;

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final ConcertRepository concertRepository;

    private Long seatId;
    private List<Long> userIds;

    @Autowired
    ReservationConcurrencyTest(
        ReservationService reservationService,
        ReservationRepository reservationRepository,
        SeatRepository seatRepository,
        UserRepository userRepository,
        ConcertRepository concertRepository
    ) {
        this.reservationService = reservationService;
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
        this.concertRepository = concertRepository;
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();

        Concert concert = concertRepository.save(
            Concert.builder()
                .concertAt(LocalDateTime.of(2026, 6, 30, 19, 0))
                .title("Concurrency Test " + suffix)
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
        userIds = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            User user = userRepository.save(
                User.builder()
                    .name("concurrency-user-" + i)
                    .email("concurrency-" + i + "-" + suffix + "@test.com")
                    .createdAt(LocalDateTime.now())
                    .build()
            );

            userIds.add(user.getId());
        }
    }

    @Test
    void pessimistic_lock으로_동시에_같은_좌석을_예약하면_한명만_성공한다()
        throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger seatUnavailableCount = new AtomicInteger();
        AtomicInteger unexpectedFailureCount = new AtomicInteger();

        try {
            for (Long userId : userIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        reservationService.createReservation(
                            new ReservationRequest(userId, seatId)
                        );

                        successCount.incrementAndGet();
                    } catch (SeatNotAvailableException e) {
                        seatUnavailableCount.incrementAndGet();
                    } catch (Exception e) {
                        unexpectedFailureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();

            assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(seatUnavailableCount.get()).isEqualTo(THREAD_COUNT - 1);
        assertThat(unexpectedFailureCount.get()).isZero();

        assertThat(
            reservationRepository.countBySeatIdAndStatus(
                seatId,
                ReservationStatus.PENDING
            )
        ).isEqualTo(1);

        Seat seat = seatRepository.findById(seatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
    }
}
