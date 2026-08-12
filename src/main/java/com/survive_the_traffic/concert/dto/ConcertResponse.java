package com.survive_the_traffic.concert.dto;

import com.survive_the_traffic.concert.domain.Concert;
import java.time.LocalDateTime;

public record ConcertResponse(
    Long id,
    String title,
    LocalDateTime reservationOpenAt,
    LocalDateTime concertAt
) {
    public static ConcertResponse from(Concert concert) {
        return new ConcertResponse(
            concert.getId(),
            concert.getTitle(),
            concert.getReservationOpenAt(),
            concert.getConcertAt()
        );
    }
}
