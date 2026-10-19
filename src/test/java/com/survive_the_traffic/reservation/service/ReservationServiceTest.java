package com.survive_the_traffic.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.payment.domain.Payment;
import com.survive_the_traffic.payment.domain.PaymentStatus;
import com.survive_the_traffic.payment.dto.PaymentResponse;
import com.survive_the_traffic.payment.repository.PaymentRepository;
import com.survive_the_traffic.reservation.domain.Reservation;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import com.survive_the_traffic.reservation.exception.ReservationNotFoundException;
import com.survive_the_traffic.reservation.repository.ReservationRepository;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.domain.SeatStatus;
import com.survive_the_traffic.seat.exception.SeatNotAvailableException;
import com.survive_the_traffic.seat.repository.SeatRepository;
import com.survive_the_traffic.user.domain.User;
import com.survive_the_traffic.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(
    MockitoExtension.class
)
class ReservationServiceTest {

    private static final Long USER_ID =
        10L;

    private static final Long CONCERT_ID =
        1L;

    private static final Long SEAT_ID =
        100L;


    @Mock
    private UserRepository
        userRepository;

    @Mock
    private SeatRepository
        seatRepository;

    @Mock
    private ReservationRepository
        reservationRepository;

    @Mock
    private PaymentRepository
        paymentRepository;


    private ReservationService
        reservationService;


    @BeforeEach
    void setUp() {

        reservationService =
            new ReservationService(
                seatRepository,
                reservationRepository,
                userRepository,
                paymentRepository
            );
    }


    @Test
    void 인증된_userId의_사용자로_예약을_생성한다() {

        // given
        ReservationRequest request =
            new ReservationRequest(
                CONCERT_ID,
                SEAT_ID
            );

        User user =
            mock(
                User.class
            );

        Seat seat =
            mock(
                Seat.class
            );

        Concert concert =
            mock(
                Concert.class
            );


        given(
            userRepository
                .findById(
                    USER_ID
                )
        ).willReturn(
            Optional.of(
                user
            )
        );


        /*
         * 네 SeatRepository에서 pessimistic lock 조회 메서드가
         * findById가 아니라 별도 메서드라면
         * 여기 한 줄만 실제 메서드명으로 맞춰라.
         */
        given(
            seatRepository
                .findByIdWithPessimisticLock(
                    SEAT_ID
                )
        ).willReturn(
            Optional.of(
                seat
            )
        );


        given(
            seat.getConcert()
        ).willReturn(
            concert
        );

        given(
            concert.getId()
        ).willReturn(
            CONCERT_ID
        );


        /*
         * save 결과로 반환될 Reservation.
         */
        Reservation savedReservation =
            mock(
                Reservation.class
            );


        given(
            reservationRepository
                .save(
                    any(
                        Reservation.class
                    )
                )
        ).willReturn(
            savedReservation
        );


        given(
            savedReservation.getId()
        ).willReturn(
            1L
        );

        given(
            savedReservation.getSeat()
        ).willReturn(
            seat
        );

        given(
            savedReservation.getStatus()
        ).willReturn(
            ReservationStatus.PENDING
        );

        given(
            savedReservation.getExpiresAt()
        ).willReturn(
            LocalDateTime.of(
                2026,
                10,
                19,
                15,
                5
            )
        );

        given(
            seat.getId()
        ).willReturn(
            SEAT_ID
        );


        // when
        ReservationResponse response =
            reservationService
                .createReservation(
                    USER_ID,
                    request
                );


        // then
        then(
            userRepository
        )
            .should()
            .findById(
                USER_ID
            );


        then(
            seatRepository
        )
            .should()
            .findByIdWithPessimisticLock(
                SEAT_ID
            );


        then(
            seat
        )
            .should()
            .hold();


        ArgumentCaptor<Reservation>
            reservationCaptor =
            ArgumentCaptor.forClass(
                Reservation.class
            );


        then(
            reservationRepository
        )
            .should()
            .save(
                reservationCaptor.capture()
            );


        assertThat(
            reservationCaptor.getValue()
        ).isNotNull();


        assertThat(
            response
        ).isNotNull();
    }


    @Test
    void request에_userId가_없어도_인증_identity로_user를_조회한다() {

        // given
        ReservationRequest request =
            new ReservationRequest(
                CONCERT_ID,
                SEAT_ID
            );

        User user =
            mock(
                User.class
            );

        Seat seat =
            mock(
                Seat.class
            );

        Concert concert =
            mock(
                Concert.class
            );

        Reservation savedReservation =
            mock(
                Reservation.class
            );


        given(
            userRepository
                .findById(
                    USER_ID
                )
        ).willReturn(
            Optional.of(
                user
            )
        );


        given(
            seatRepository
                .findByIdWithPessimisticLock(
                    SEAT_ID
                )
        ).willReturn(
            Optional.of(
                seat
            )
        );


        given(
            seat.getConcert()
        ).willReturn(
            concert
        );

        given(
            concert.getId()
        ).willReturn(
            CONCERT_ID
        );

        given(
            reservationRepository
                .save(
                    any(
                        Reservation.class
                    )
                )
        ).willReturn(
            savedReservation
        );


        given(
            savedReservation.getId()
        ).willReturn(
            1L
        );

        given(
            savedReservation.getSeat()
        ).willReturn(
            seat
        );

        given(
            savedReservation.getStatus()
        ).willReturn(
            ReservationStatus.PENDING
        );

        given(
            savedReservation.getExpiresAt()
        ).willReturn(
            LocalDateTime.of(
                2026,
                10,
                19,
                15,
                5
            )
        );

        given(
            seat.getId()
        ).willReturn(
            SEAT_ID
        );


        // when
        reservationService
            .createReservation(
                USER_ID,
                request
            );


        // then
        then(
            userRepository
        )
            .should()
            .findById(
                USER_ID
            );
    }


    @Test
    void AVAILABLE이_아닌_좌석은_예약할_수_없다() {

        // given
        ReservationRequest request =
            new ReservationRequest(
                CONCERT_ID,
                SEAT_ID
            );

        User user =
            mock(
                User.class
            );

        Seat seat =
            mock(
                Seat.class
            );

        Concert concert =
            mock(
                Concert.class
            );


        given(
            userRepository
                .findById(
                    USER_ID
                )
        ).willReturn(
            Optional.of(
                user
            )
        );


        given(
            seatRepository
                .findByIdWithPessimisticLock(
                    SEAT_ID
                )
        ).willReturn(
            Optional.of(
                seat
            )
        );


        given(
            seat.getConcert()
        ).willReturn(
            concert
        );

        given(
            concert.getId()
        ).willReturn(
            CONCERT_ID
        );


        /*
         * ReservationService는 getStatus()를 검사하지 않고
         * Seat.hold()에게 상태 검증을 위임한다.
         */
        doThrow(
            new SeatNotAvailableException()
        )
            .when(
                seat
            )
            .hold();


        // when + then
        assertThatThrownBy(
            () ->
                reservationService
                    .createReservation(
                        USER_ID,
                        request
                    )
        )
            .isInstanceOf(
                SeatNotAvailableException.class
            );


        then(
            seatRepository
        )
            .should()
            .findByIdWithPessimisticLock(
                SEAT_ID
            );


        then(
            reservationRepository
        )
            .shouldHaveNoInteractions();
    }

    @Test
    void 인증된_사용자에게_속하지_않은_예약은_결제할_수_없다() {

        // given
        Long reservationId =
            1000L;

        given(
            reservationRepository
                .findByIdAndUser_Id(
                    reservationId,
                    USER_ID
                )
        ).willReturn(
            Optional.empty()
        );


        // when + then
        assertThatThrownBy(
            () ->
                reservationService
                    .createPayment(
                        USER_ID,
                        reservationId
                    )
        )
            .isInstanceOf(
                ReservationNotFoundException.class
            );


        then(
            reservationRepository
        )
            .should()
            .findByIdAndUser_Id(
                reservationId,
                USER_ID
            );

        then(
            paymentRepository
        )
            .shouldHaveNoInteractions();
    }

    @Test
    void 인증된_사용자는_자신의_예약을_결제할_수_있다() {

        // given
        Long reservationId = 1000L;
        Long paymentId = 5000L;
        Long amount = 150000L;

        Reservation reservation =
            mock(Reservation.class);

        Seat seat =
            mock(Seat.class);

        Payment savedPayment =
            mock(Payment.class);

        given(
            reservationRepository
                .findByIdAndUser_Id(
                    reservationId,
                    USER_ID
                )
        ).willReturn(
            Optional.of(reservation)
        );

        given(
            reservation.getSeat()
        ).willReturn(
            seat
        );

        given(
            seat.getPrice()
        ).willReturn(
            amount
        );

        given(
            paymentRepository.save(
                any(Payment.class)
            )
        ).willReturn(
            savedPayment
        );

        given(
            savedPayment.getId()
        ).willReturn(
            paymentId
        );

        given(
            savedPayment.getReservation()
        ).willReturn(
            reservation
        );

        given(
            reservation.getId()
        ).willReturn(
            reservationId
        );

        given(
            savedPayment.getStatus()
        ).willReturn(
            PaymentStatus.SUCCESS
        );

        given(
            savedPayment.getAmount()
        ).willReturn(
            amount
        );


        // when
        PaymentResponse response =
            reservationService.createPayment(
                USER_ID,
                reservationId
            );


        // then
        assertThat(response)
            .isNotNull();

        then(
            reservationRepository
        )
            .should()
            .findByIdAndUser_Id(
                reservationId,
                USER_ID
            );

        then(
            reservation
        )
            .should()
            .validatePayable(
                any(LocalDateTime.class)
            );

        then(
            paymentRepository
        )
            .should()
            .save(
                any(Payment.class)
            );

        then(
            reservation
        )
            .should()
            .confirm();

        then(
            seat
        )
            .should()
            .sell();
    }
}