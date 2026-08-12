package com.survive_the_traffic.seat.dto;

import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.domain.SeatStatus;

public record SeatResponse(
        Long seatId,
        String section,
        Integer row,
        Integer number,
        Long price,
        SeatStatus status) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getSection(),
                seat.getRowNumber(),
                seat.getSeatNumber(),
                seat.getPrice(),
                seat.getStatus()
        );
    }

}
