package com.survive_the_traffic.reservation.dto;

import com.survive_the_traffic.reservation.domain.Reservation;
import com.survive_the_traffic.reservation.domain.ReservationStatus;

public record ReservationDetailResponse(
    Long reservationId,
    String concertTitle,
    String section,
    Integer row,
    Integer seatNumber,
    Long price,
    ReservationStatus status) {
    public static ReservationDetailResponse from(Reservation reservation) {
        return new ReservationDetailResponse(
            reservation.getId(),
            reservation.getSeat().getConcert().getTitle(),
            reservation.getSeat().getSection(),
            reservation.getSeat().getRowNumber(),
            reservation.getSeat().getSeatNumber(),
            reservation.getSeat().getPrice(),
            reservation.getStatus()
        );
    }
}
