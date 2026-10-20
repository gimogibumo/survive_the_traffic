package com.survive_the_traffic.reservation.dto;

import com.survive_the_traffic.reservation.domain.Reservation;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import java.time.LocalDateTime;

public record ReservationDetailResponse(
    Long reservationId,
    String concertTitle,
    String section,
    Integer row,
    Integer seatNumber,
    Long price,
    ReservationStatus status,
    LocalDateTime expiresAt) {
    public static ReservationDetailResponse from(Reservation reservation) {
        return new ReservationDetailResponse(
            reservation.getId(),
            reservation.getSeat().getConcert().getTitle(),
            reservation.getSeat().getSection(),
            reservation.getSeat().getRowNumber(),
            reservation.getSeat().getSeatNumber(),
            reservation.getSeat().getPrice(),
            reservation.getStatus(),
            reservation.getExpiresAt()
        );
    }
}
