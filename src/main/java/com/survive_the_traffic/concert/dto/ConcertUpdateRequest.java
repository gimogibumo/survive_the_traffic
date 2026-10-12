package com.survive_the_traffic.concert.dto;

import java.time.LocalDateTime;

public record ConcertUpdateRequest(
    String title,
    LocalDateTime reservationOpenAt,
    LocalDateTime concertAt
) {

}
