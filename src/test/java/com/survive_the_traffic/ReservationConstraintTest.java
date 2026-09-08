package com.survive_the_traffic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.concert.repository.ConcertRepository;
import com.survive_the_traffic.reservation.domain.Reservation;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.reservation.repository.ReservationRepository;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.domain.SeatStatus;
import com.survive_the_traffic.seat.repository.SeatRepository;
import com.survive_the_traffic.user.domain.User;
import com.survive_the_traffic.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class ReservationConstraintTest {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final ConcertRepository concertRepository;

    private Seat seat;
    private User user1;
    private User user2;

    @Autowired
    ReservationConstraintTest(
        ReservationRepository reservationRepository,
        SeatRepository seatRepository,
        UserRepository userRepository,
        ConcertRepository concertRepository
    ) {
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
                .title("Constraint Test " + suffix)
                .reservationOpenAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                .createdAt(LocalDateTime.now())
                .build()
        );

        seat = seatRepository.save(
            Seat.builder()
                .section("A")
                .rowNumber(1)
                .seatNumber(1)
                .price(10000L)
                .status(SeatStatus.AVAILABLE)
                .concert(concert)
                .build()
        );

        user1 = saveUser("constraint-user-1", suffix);
        user2 = saveUser("constraint-user-2", suffix);
    }

    @Test
    void 같은_좌석에는_PENDING_예약을_두개_생성할_수_없다() {
        reservationRepository.saveAndFlush(
            reservation(user1, ReservationStatus.PENDING)
        );

        assertThatThrownBy(() ->
            reservationRepository.saveAndFlush(
                reservation(user2, ReservationStatus.PENDING)
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void CONFIRMED_예약도_활성_예약으로_판단한다() {
        reservationRepository.saveAndFlush(
            reservation(user1, ReservationStatus.CONFIRMED)
        );

        assertThatThrownBy(() ->
            reservationRepository.saveAndFlush(
                reservation(user2, ReservationStatus.PENDING)
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 만료된_예약이_있으면_같은_좌석에_새로운_예약을_생성할_수_있다() {
        reservationRepository.saveAndFlush(
            reservation(user1, ReservationStatus.EXPIRED)
        );
        reservationRepository.saveAndFlush(
            reservation(user2, ReservationStatus.PENDING)
        );

        assertThat(reservationRepository.countBySeatId(seat.getId()))
            .isEqualTo(2);
    }

    private User saveUser(String name, String suffix) {
        return userRepository.save(
            User.builder()
                .name(name)
                .email(name + "-" + suffix + "@test.com")
                .createdAt(LocalDateTime.now())
                .build()
        );
    }

    private Reservation reservation(
        User user,
        ReservationStatus status
    ) {
        return Reservation.builder()
            .user(user)
            .seat(seat)
            .status(status)
            .expiresAt(LocalDateTime.of(2026, 5, 1, 11, 0))
            .createdAt(LocalDateTime.of(2026, 5, 1, 10, 0))
            .build();
    }
}
