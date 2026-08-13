package com.survive_the_traffic.reservation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.survive_the_traffic.payment.domain.Payment;
import com.survive_the_traffic.payment.domain.PaymentStatus;
import com.survive_the_traffic.payment.dto.PaymentResponse;
import com.survive_the_traffic.payment.repository.PaymentRepository;
import com.survive_the_traffic.reservation.domain.Reservation;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import com.survive_the_traffic.reservation.exception.ReservationExpiredException;
import com.survive_the_traffic.reservation.exception.ReservationNotFoundException;
import com.survive_the_traffic.reservation.exception.ReservationNotPendingException;
import com.survive_the_traffic.reservation.repository.ReservationRepository;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.domain.SeatStatus;
import com.survive_the_traffic.seat.exception.SeatNotAvailableException;
import com.survive_the_traffic.seat.exception.SeatNotFoundException;
import com.survive_the_traffic.seat.repository.SeatRepository;
import com.survive_the_traffic.user.domain.User;
import com.survive_the_traffic.user.exception.UserNotFoundException;
import com.survive_the_traffic.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    @DisplayName("AVAILABLE 좌석을 정상적으로 예약한다")
    void createReservation_success() {

        // given
        Long userId = 1L;
        Long seatId = 101L;

        ReservationRequest request =
            new ReservationRequest(userId, seatId);

        User user = mock(User.class);
        Seat seat = mock(Seat.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(seatRepository.findById(seatId))
            .thenReturn(Optional.of(seat));

        when(seat.getId())
            .thenReturn(seatId);

        Reservation saved = mock(Reservation.class);

        when(saved.getId())
            .thenReturn(1001L);

        when(saved.getSeat())
            .thenReturn(seat);

        when(saved.getStatus())
            .thenReturn(ReservationStatus.PENDING);

        when(saved.getExpiresAt())
            .thenReturn(LocalDateTime.now().plusMinutes(5));

        when(reservationRepository.save(any(Reservation.class)))
            .thenReturn(saved);


        // when
        ReservationResponse result =
            reservationService.createReservation(request);


        // then
        assertEquals(1001L, result.reservationId());
        assertEquals(101L, result.seatId());
        assertEquals(
            ReservationStatus.PENDING,
            result.status()
        );

        ArgumentCaptor<Reservation> captor =
            ArgumentCaptor.forClass(Reservation.class);

        verify(reservationRepository)
            .save(captor.capture());

        Reservation createdReservation =
            captor.getValue();

        assertEquals(
            ReservationStatus.PENDING,
            createdReservation.getStatus()
        );

        assertSame(user, createdReservation.getUser());
        assertSame(seat, createdReservation.getSeat());
    }

    @Test
    @DisplayName("존재하지 않는 사용자가 예약하면 예외가 발생한다")
    void createReservation_userNotFound() {

        // given
        Long userId = 999L;
        Long seatId = 101L;

        ReservationRequest request =
            new ReservationRequest(userId, seatId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThrows(
            UserNotFoundException.class,
            () -> reservationService.createReservation(request)
        );

        verify(userRepository).findById(userId);

        verifyNoInteractions(seatRepository);
        verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("존재하지 않는 좌석을 예약하면 예외가 발생한다")
    void createReservation_seatNotFound() {

        // given
        Long userId = 1L;
        Long seatId = 999L;

        ReservationRequest request =
            new ReservationRequest(userId, seatId);

        User user = mock(User.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(seatRepository.findById(seatId))
            .thenReturn(Optional.empty());

        // when & then
        assertThrows(
            SeatNotFoundException.class,
            () -> reservationService.createReservation(request)
        );

        verify(userRepository).findById(userId);
        verify(seatRepository).findById(seatId);

        verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("이미 예약된 좌석은 다시 예약할 수 없다")
    void createReservation_seatNotAvailable() {

        // given
        Long userId = 1L;
        Long seatId = 101L;

        ReservationRequest request =
            new ReservationRequest(userId, seatId);

        User user = mock(User.class);
        Seat seat = mock(Seat.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(seatRepository.findById(seatId))
            .thenReturn(Optional.of(seat));

        doThrow(new SeatNotAvailableException())
            .when(seat)
            .hold();

        // when & then
        assertThrows(
            SeatNotAvailableException.class,
            () -> reservationService.createReservation(request)
        );

        verify(reservationRepository, never())
            .save(any());
    }


    @Test
    @DisplayName("PENDING 예약을 정상적으로 결제한다")
    void createPayment_success() {

        // given
        Long reservationId = 1001L;

        Reservation reservation = mock(Reservation.class);
        Seat seat = mock(Seat.class);
        Payment savedPayment = mock(Payment.class);

        when(reservationRepository.findById(reservationId))
            .thenReturn(Optional.of(reservation));

        // validatePayable()은 mock void method라
        // 별도 설정이 없으면 정상 통과

        when(reservation.getSeat())
            .thenReturn(seat);

        when(reservation.getId())
            .thenReturn(reservationId);

        when(seat.getPrice())
            .thenReturn(150000L);

        when(savedPayment.getId())
            .thenReturn(5001L);

        when(savedPayment.getReservation())
            .thenReturn(reservation);

        when(savedPayment.getStatus())
            .thenReturn(PaymentStatus.SUCCESS);

        when(savedPayment.getAmount())
            .thenReturn(150000L);

        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(savedPayment);

        // when
        PaymentResponse result =
            reservationService.createPayment(reservationId);

        // then
        assertEquals(5001L, result.paymentId());
        assertEquals(1001L, result.reservationId());
        assertEquals(PaymentStatus.SUCCESS, result.status());
        assertEquals(150000L, result.amount());

        verify(reservation)
            .validatePayable(any(LocalDateTime.class));

        verify(paymentRepository)
            .save(any(Payment.class));

        verify(reservation)
            .confirm();

        verify(seat)
            .sell();
    }

    @Test
    @DisplayName("존재하지 않는 예약을 결제하면 예외가 발생한다")
    void createPayment_reservationNotFound() {

        // given
        Long reservationId = 999L;

        when(reservationRepository.findById(reservationId))
            .thenReturn(Optional.empty());

        // when & then
        assertThrows(
            ReservationNotFoundException.class,
            () -> reservationService.createPayment(reservationId)
        );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("PENDING 상태가 아닌 예약은 결제할 수 없다")
    void createPayment_notPending() {

        // given
        Long reservationId = 1001L;

        Reservation reservation = mock(Reservation.class);

        when(reservationRepository.findById(reservationId))
            .thenReturn(Optional.of(reservation));

        doThrow(new ReservationNotPendingException())
            .when(reservation)
            .validatePayable(any(LocalDateTime.class));

        // when & then
        assertThrows(
            ReservationNotPendingException.class,
            () -> reservationService.createPayment(reservationId)
        );

        verify(reservation)
            .validatePayable(any(LocalDateTime.class));

        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("만료된 예약은 결제할 수 없다")
    void createPayment_expired() {

        // given
        Long reservationId = 1001L;

        Reservation reservation = mock(Reservation.class);

        when(reservationRepository.findById(reservationId))
            .thenReturn(Optional.of(reservation));

        doThrow(new ReservationExpiredException())
            .when(reservation)
            .validatePayable(any(LocalDateTime.class));

        // when & then
        assertThrows(
            ReservationExpiredException.class,
            () -> reservationService.createPayment(reservationId)
        );

        verify(reservation)
            .validatePayable(any(LocalDateTime.class));

        verifyNoInteractions(paymentRepository);
    }
}