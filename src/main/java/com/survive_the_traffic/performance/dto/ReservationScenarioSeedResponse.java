package com.survive_the_traffic.performance.dto;

import java.util.List;

public record ReservationScenarioSeedResponse(
    Long concertId,
    List<Long> userIds,
    List<Long> seatIds
) {
}
