package com.survive_the_traffic.performance.controller;

import com.survive_the_traffic.performance.dto.ExplainResponse;
import com.survive_the_traffic.performance.dto.ReservationScenarioSeedResponse;
import com.survive_the_traffic.performance.dto.SeatReadSeedResponse;
import com.survive_the_traffic.performance.dto.UserReservationSeedResponse;
import com.survive_the_traffic.performance.service.PerformanceSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("performance")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/performance")
public class PerformanceController {

    private final PerformanceSeedService performanceSeedService;

    @PostMapping("/seed/seat-read")
    public SeatReadSeedResponse seedSeatReadData(
        @RequestParam(defaultValue = "10000") int seatCount
    ) {
        return performanceSeedService.seedSeatReadData(seatCount);
    }

    @PostMapping("/seed/hot-seat")
    public ReservationScenarioSeedResponse seedHotSeatData(
        @RequestParam(defaultValue = "100") int userCount
    ) {
        return performanceSeedService.seedHotSeatData(userCount);
    }

    @PostMapping("/seed/distributed-seats")
    public ReservationScenarioSeedResponse seedDistributedSeatData(
        @RequestParam(defaultValue = "100") int count
    ) {
        return performanceSeedService.seedDistributedSeatData(count);
    }

    @PostMapping("/seed/user-reservations")
    public UserReservationSeedResponse seedUserReservationData(
        @RequestParam(defaultValue = "100") int reservationCount
    ) {
        return performanceSeedService.seedUserReservationData(reservationCount);
    }

    @GetMapping("/explain/concerts/{concertId}/seats")
    public ExplainResponse explainSeatsByConcert(
        @PathVariable Long concertId,
        @RequestParam(defaultValue = "true") boolean analyze
    ) {
        return performanceSeedService.explainSeatsByConcert(
            concertId,
            analyze
        );
    }
}
