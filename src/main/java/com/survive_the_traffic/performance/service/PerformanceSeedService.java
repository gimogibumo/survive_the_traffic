package com.survive_the_traffic.performance.service;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.performance.dto.ExplainResponse;
import com.survive_the_traffic.performance.dto.ReservationScenarioSeedResponse;
import com.survive_the_traffic.performance.dto.SeatReadSeedResponse;
import com.survive_the_traffic.performance.dto.UserReservationSeedResponse;
import com.survive_the_traffic.reservation.domain.Reservation;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.domain.SeatStatus;
import com.survive_the_traffic.user.domain.User;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("performance")
@Service
@RequiredArgsConstructor
public class PerformanceSeedService {

    private static final int BATCH_SIZE = 500;

    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public SeatReadSeedResponse seedSeatReadData(int seatCount) {
        LocalDateTime now = LocalDateTime.now(clock);
        Concert concert = persistConcert("Seat Read",now);

        for (int i = 0; i < seatCount; i++) {
            entityManager.persist(
                buildSeat(
                    concert,
                    rowNumberOf(i),
                    seatNumberOf(i),
                    SeatStatus.AVAILABLE
                )
            );

            flushAndClearIfNeeded(i);
        }

        entityManager.flush();

        return new SeatReadSeedResponse(concert.getId(), seatCount);
    }

    @Transactional
    public ReservationScenarioSeedResponse seedHotSeatData(int userCount) {
        LocalDateTime now = LocalDateTime.now(clock);
        Concert concert = persistConcert("Hot Seat", now);
        Seat seat = buildSeat(concert, 1, 1, SeatStatus.AVAILABLE);
        entityManager.persist(seat);

        List<Long> userIds = new ArrayList<>();

        for (int i = 0; i < userCount; i++) {
            User user = persistUser("hot-seat-user-" + i, now);
            userIds.add(user.getId());
        }

        entityManager.flush();

        return new ReservationScenarioSeedResponse(
            concert.getId(),
            userIds,
            List.of(seat.getId())
        );
    }

    @Transactional
    public ReservationScenarioSeedResponse seedDistributedSeatData(int count) {
        LocalDateTime now = LocalDateTime.now(clock);
        Concert concert = persistConcert("Distributed Seats", now);
        List<Long> userIds = new ArrayList<>();
        List<Long> seatIds = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            User user = persistUser("distributed-user-" + i, now);
            Seat seat = buildSeat(
                concert,
                rowNumberOf(i),
                seatNumberOf(i),
                SeatStatus.AVAILABLE
            );

            entityManager.persist(seat);

            userIds.add(user.getId());
            seatIds.add(seat.getId());

            flushAndClearIfNeeded(i);
        }

        entityManager.flush();

        return new ReservationScenarioSeedResponse(
            concert.getId(),
            userIds,
            seatIds
        );
    }

    @Transactional
    public UserReservationSeedResponse seedUserReservationData(int reservationCount) {

        LocalDateTime now = LocalDateTime.now(clock);
        Concert concert = persistConcert("User Reservations", now);
        User user = persistUser("reservation-history-user", now);

        for (int i = 0; i < reservationCount; i++) {
            Seat seat = buildSeat(
                concert,
                rowNumberOf(i),
                seatNumberOf(i),
                SeatStatus.SOLD
            );
            entityManager.persist(seat);
            entityManager.persist(
                Reservation.builder()
                    .user(user)
                    .seat(seat)
                    .status(ReservationStatus.CONFIRMED)
                    .createdAt(now)
                    .expiresAt(now.plusMinutes(5))
                    .build()
            );

            flushAndClearIfNeeded(i);
        }

        entityManager.flush();

        return new UserReservationSeedResponse(
            user.getId(),
            reservationCount
        );
    }

    @Transactional(readOnly = true)
    public ExplainResponse explainSeatsByConcert(Long concertId, boolean analyze) {
        String query =
            "SELECT * FROM seat WHERE concert_id = " + concertId;
        String explainSql = analyze
            ? "EXPLAIN (ANALYZE, BUFFERS) " + query
            : "EXPLAIN " + query;

        @SuppressWarnings("unchecked")
        List<String> plan = entityManager
            .createNativeQuery(explainSql)
            .getResultList();

        return new ExplainResponse(
            query,
            plan
        );
    }

    private Concert persistConcert(String name, LocalDateTime now) {
        Concert concert = Concert.builder()
            .title(name + " " + UUID.randomUUID())
            .reservationOpenAt(now.minusDays(1))
            .concertAt(now.plusDays(30))
            .createdAt(now)
            .build();

        entityManager.persist(concert);

        return concert;
    }

    private User persistUser(String name, LocalDateTime now) {
        User user = User.builder()
            .name(name)
            .email(name + "-" + UUID.randomUUID() + "@test.com")
            .createdAt(now)
            .build();

        entityManager.persist(user);

        return user;
    }

    private Seat buildSeat(
        Concert concert,
        int rowNumber,
        int seatNumber,
        SeatStatus status
    ) {
        return Seat.builder()
            .concert(concert)
            .section("A")
            .rowNumber(rowNumber)
            .seatNumber(seatNumber)
            .price(10000L)
            .status(status)
            .build();
    }

    private int rowNumberOf(int index) {
        return index / 100 + 1;
    }

    private int seatNumberOf(int index) {
        return index % 100 + 1;
    }

    private void flushAndClearIfNeeded(int index) {
        if ((index + 1) % BATCH_SIZE == 0) {
            entityManager.flush();
            entityManager.clear();
        }
    }
}
