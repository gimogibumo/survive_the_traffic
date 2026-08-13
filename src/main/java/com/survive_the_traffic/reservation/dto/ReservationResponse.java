package com.survive_the_traffic.reservation.dto;

import com.survive_the_traffic.reservation.domain.ReservationStatus;
import java.time.LocalDateTime;

public record ReservationResponse(
    Long reservationId,
    Long seatId,
    ReservationStatus status,
    LocalDateTime expiresAt
) {
    public ReservationResponse(Long reservationId, Long seatId, ReservationStatus status, LocalDateTime expiresAt) {
        this.reservationId = reservationId;
        this.seatId = seatId;
        this.status = status;
        this.expiresAt = expiresAt;
    }

}
