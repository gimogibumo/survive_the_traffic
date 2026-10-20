package com.survive_the_traffic.reservation.service;

import com.survive_the_traffic.common.exception.SeatConcertMismatchException;
import com.survive_the_traffic.payment.domain.Payment;
import com.survive_the_traffic.payment.dto.PaymentResponse;
import com.survive_the_traffic.payment.repository.PaymentRepository;
import com.survive_the_traffic.reservation.dto.ReservationDetailResponse;
import com.survive_the_traffic.reservation.exception.ReservationNotFoundException;
import com.survive_the_traffic.reservation.domain.Reservation;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import com.survive_the_traffic.reservation.exception.ReservationNotPendingException;
import com.survive_the_traffic.reservation.repository.ReservationRepository;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.exception.SeatNotFoundException;
import com.survive_the_traffic.seat.repository.SeatRepository;
import com.survive_the_traffic.user.domain.User;
import com.survive_the_traffic.user.exception.UserNotFoundException;
import com.survive_the_traffic.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    @Transactional
    public ReservationResponse createReservation(Long userId, @Validated ReservationRequest request) {

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Seat seat = seatRepository.findByIdWithPessimisticLock(request.seatId()).orElseThrow(SeatNotFoundException::new);

        if(!seat.getConcert().getId().equals(request.concertId())) {
            throw new SeatConcertMismatchException();
        }

        seat.hold();
        seatRepository.save(seat);

        LocalDateTime now = LocalDateTime.now(clock);
        Reservation reservation = Reservation.builder()
            .seat(seat)
            .user(user)
            .createdAt(now)
            .expiresAt(now.plusMinutes(5))
            .status(ReservationStatus.PENDING)
            .build();
        Reservation saved = reservationRepository.save(reservation);

        return new ReservationResponse(
            saved.getId(),
            saved.getSeat().getId(),
            saved.getStatus(),
            saved.getExpiresAt()
        );

    }

    @Transactional
    public PaymentResponse createPayment(Long userId, Long reservationId) {
        Reservation reservation =
            reservationRepository.findByIdAndUser_Id(reservationId, userId)
                .orElseThrow(ReservationNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        reservation.validatePayable(now);
        //결제 했다 치고
        Payment payment = Payment.builder()
            .reservation(reservation)
            .amount(reservation.getSeat().getPrice())
            .status(com.survive_the_traffic.payment.domain.PaymentStatus.SUCCESS)
            .paymentKey(UUID.randomUUID().toString())
            .createdAt(now)
            .build();
        Payment savedPayment = paymentRepository.save(payment);


        reservation.confirm();
        reservation.getSeat().sell();
        return new PaymentResponse(
            savedPayment.getId(),
            savedPayment.getReservation().getId(),
            savedPayment.getStatus(),
            savedPayment.getAmount()
        );
    }

    @Transactional(readOnly = true)
    public ReservationDetailResponse getReservation(Long userId, Long reservationId) {

        Reservation reservation = reservationRepository.findByIdAndUser_Id(reservationId,userId)
            .orElseThrow(ReservationNotFoundException::new);
        return ReservationDetailResponse.from(reservation);
    }

}
