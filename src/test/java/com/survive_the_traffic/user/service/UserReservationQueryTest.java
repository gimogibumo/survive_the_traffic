package com.survive_the_traffic.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.performance.service.PerformanceQueryService;
import com.survive_the_traffic.concert.repository.ConcertRepository;
import com.survive_the_traffic.reservation.domain.Reservation;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.reservation.dto.ReservationDetailResponse;
import com.survive_the_traffic.reservation.repository.ReservationRepository;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.domain.SeatStatus;
import com.survive_the_traffic.seat.repository.SeatRepository;
import com.survive_the_traffic.user.domain.User;
import com.survive_the_traffic.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("performance")
class UserReservationQueryTest {

    @Autowired
    private PerformanceQueryService performanceQueryService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void 사용자_예약_조회는_seat_concert를_fetch_join으로_조회한다() {
        User user = userRepository.save(
            User.builder()
                .name("query-count-user")
                .email("query-count-" + UUID.randomUUID() + "@test.com")
                .createdAt(LocalDateTime.now())
                .build()
        );

        Concert concert = concertRepository.save(
            Concert.builder()
                .title("Query Count Concert " + UUID.randomUUID())
                .reservationOpenAt(LocalDateTime.now())
                .concertAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .build()
        );

        for (int i = 0; i < 100; i++) {
            Seat seat = seatRepository.save(
                Seat.builder()
                    .concert(concert)
                    .section("A")
                    .rowNumber(i / 100 + 1)
                    .seatNumber(i % 100 + 1)
                    .price(10000L)
                    .status(SeatStatus.SOLD)
                    .build()
            );

            reservationRepository.save(
                Reservation.builder()
                    .user(user)
                    .seat(seat)
                    .status(ReservationStatus.CONFIRMED)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build()
            );
        }

        Statistics statistics = entityManagerFactory
            .unwrap(SessionFactory.class)
            .getStatistics();
        statistics.clear();

        List<ReservationDetailResponse> before =
            performanceQueryService.getUserReservationsBeforeFetchJoin(user.getId());
        long beforeCount = statistics.getPrepareStatementCount();
        assertThat(beforeCount).isEqualTo(103);
        statistics.clear();

        List<ReservationDetailResponse> result =
            userService.getUserReservations(user.getId());

        assertThat(result).hasSize(100);
        assertThat(result).containsExactlyInAnyOrderElementsOf(before);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        System.out.printf("Reservation SQL count: lazy=%d, fetchJoin=%d%n",
            beforeCount, statistics.getPrepareStatementCount());
    }
}
