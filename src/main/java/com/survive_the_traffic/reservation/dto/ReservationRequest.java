package com.survive_the_traffic.reservation.dto;

import jakarta.annotation.Nonnull;

public record ReservationRequest(

    @Nonnull
    Long concertId,

    @Nonnull
    Long seatId
) {

}
