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
import com.survive_the_traffic.reservation.dto.ReservationDetailResponse;
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

    private Clock clock;


    @BeforeEach
    void setUp() {

        clock =
            Clock.fixed(
                Instant.parse(
                    "2026-10-20T13:30:00Z"
                ),
                ZoneId.of(
                    "Asia/Seoul"
                )
            );

        reservationService =
            new ReservationService(
                seatRepository,
                reservationRepository,
                userRepository,
                paymentRepository,
                clock
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


        /*
         * 인증된 userId를 이용해서
         * 실제 예약 소유자를 조회한다.
         */
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
         * 좌석 예약 시 동시성 제어를 위해
         * 비관적 락을 사용해서 좌석을 조회한다.
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


        /*
         * request.concertId와
         * seat가 실제로 속한 concertId가 같은지
         * ReservationService에서 검증한다.
         */
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
         * Repository 저장 이후 반환될 Reservation을 mock한다.
         *
         * 실제 생성되는 Reservation과
         * DB save 이후 반환값은 테스트에서 별도로 구분한다.
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


        /*
         * ReservationResponse 생성에 사용되는 값들.
         */
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
                20,
                22,
                35
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

        /*
         * 1.
         * client가 전달한 userId가 아니라
         * ReservationService에 전달된 인증 userId로
         * User를 조회했는지 확인한다.
         */
        then(
            userRepository
        )
            .should()
            .findById(
                USER_ID
            );


        /*
         * 2.
         * 예약할 좌석을 비관적 락으로 조회했는지 확인한다.
         */
        then(
            seatRepository
        )
            .should()
            .findByIdWithPessimisticLock(
                SEAT_ID
            );


        /*
         * 3.
         * AVAILABLE 좌석을 HELD 상태로 전환하는
         * 도메인 메서드를 호출했는지 확인한다.
         */
        then(
            seat
        )
            .should()
            .hold();


        /*
         * 4.
         * 변경된 Seat를 저장했는지 확인한다.
         */
        then(
            seatRepository
        )
            .should()
            .save(
                seat
            );


        /*
         * 5.
         * 실제 ReservationService가 생성해서
         * Repository에 넘긴 Reservation 객체를 잡는다.
         */
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


        Reservation capturedReservation =
            reservationCaptor.getValue();


        assertThat(
            capturedReservation
        ).isNotNull();


        /*
         * 테스트에 주입한 Clock:
         *
         * Instant:
         * 2026-10-20T13:30:00Z
         *
         * Zone:
         * Asia/Seoul
         *
         * 따라서 LocalDateTime.now(clock)은
         * 2026-10-20T22:30:00 이어야 한다.
         */
        LocalDateTime expectedCreatedAt =
            LocalDateTime.of(
                2026,
                10,
                20,
                22,
                30
            );

        /*
         * Reservation hold TTL은 5분이므로
         * expiresAt은 정확히 22:35이어야 한다.
         */
        LocalDateTime expectedExpiresAt =
            LocalDateTime.of(
                2026,
                10,
                20,
                22,
                35
            );


        /*
         * 6.
         * 생성된 Reservation의 소유자가
         * 인증된 user인지 확인한다.
         */
        assertThat(
            capturedReservation.getUser()
        ).isSameAs(
            user
        );


        /*
         * 7.
         * 예약 대상 좌석이 맞는지 확인한다.
         */
        assertThat(
            capturedReservation.getSeat()
        ).isSameAs(
            seat
        );


        /*
         * 8.
         * 최초 예약 상태는 PENDING이어야 한다.
         */
        assertThat(
            capturedReservation.getStatus()
        ).isEqualTo(
            ReservationStatus.PENDING
        );


        /*
         * 9.
         * 시스템 기본 시간을 직접 사용하지 않고
         * 주입된 Clock을 기준으로 createdAt을 생성했는지 확인한다.
         */
        assertThat(
            capturedReservation.getCreatedAt()
        ).isEqualTo(
            expectedCreatedAt
        );


        /*
         * 10.
         * expiresAt이 createdAt 기준 정확히 5분 뒤인지 확인한다.
         */
        assertThat(
            capturedReservation.getExpiresAt()
        ).isEqualTo(
            expectedExpiresAt
        );


        /*
         * 11.
         * 최종 응답도 정상 생성되었는지 확인한다.
         */
        assertThat(
            response
        ).isNotNull();

        assertThat(
            response.reservationId()
        ).isEqualTo(
            1L
        );

        assertThat(
            response.seatId()
        ).isEqualTo(
            SEAT_ID
        );

        assertThat(
            response.status()
        ).isEqualTo(
            ReservationStatus.PENDING
        );

        assertThat(
            response.expiresAt()
        ).isEqualTo(
            expectedExpiresAt
        );
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

    @Test
    void 인증된_사용자는_자신의_예약_상세를_조회할_수_있다() {

        // given
        Long reservationId = 1000L;
        LocalDateTime expiresAt =
            LocalDateTime.of(
                2026,
                10,
                20,
                14,
                30
            );

        Reservation reservation =
            mock(
                Reservation.class
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
            reservationRepository
                .findByIdAndUser_Id(
                    reservationId,
                    USER_ID
                )
        ).willReturn(
            Optional.of(
                reservation
            )
        );

        given(
            reservation.getId()
        ).willReturn(
            reservationId
        );

        given(
            reservation.getSeat()
        ).willReturn(
            seat
        );

        given(
            seat.getConcert()
        ).willReturn(
            concert
        );

        given(
            concert.getTitle()
        ).willReturn(
            "Survive the Traffic Live"
        );

        given(
            seat.getSection()
        ).willReturn(
            "A"
        );

        given(
            seat.getRowNumber()
        ).willReturn(
            3
        );

        given(
            seat.getSeatNumber()
        ).willReturn(
            12
        );

        given(
            seat.getPrice()
        ).willReturn(
            150000L
        );

        given(
            reservation.getStatus()
        ).willReturn(
            ReservationStatus.PENDING
        );

        given(
            reservation.getExpiresAt()
        ).willReturn(
            expiresAt
        );


        // when
        ReservationDetailResponse response =
            reservationService.getReservation(
                USER_ID,
                reservationId
            );


        // then
        assertThat(
            response.reservationId()
        ).isEqualTo(
            reservationId
        );

        assertThat(
            response.concertTitle()
        ).isEqualTo(
            "Survive the Traffic Live"
        );

        assertThat(
            response.section()
        ).isEqualTo(
            "A"
        );

        assertThat(
            response.row()
        ).isEqualTo(
            3
        );

        assertThat(
            response.seatNumber()
        ).isEqualTo(
            12
        );

        assertThat(
            response.price()
        ).isEqualTo(
            150000L
        );

        assertThat(
            response.status()
        ).isEqualTo(
            ReservationStatus.PENDING
        );

        assertThat(
            response.expiresAt()
        ).isEqualTo(
            expiresAt
        );

        then(
            reservationRepository
        )
            .should()
            .findByIdAndUser_Id(
                reservationId,
                USER_ID
            );
    }

    @Test
    void 인증된_사용자에게_속하지_않은_예약_상세는_조회할_수_없다() {

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
                    .getReservation(
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
    }
}