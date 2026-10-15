package com.survive_the_traffic.concert.controller;

import com.survive_the_traffic.concert.dto.ConcertResponse;
import com.survive_the_traffic.concert.dto.ConcertUpdateRequest;
import com.survive_the_traffic.concert.service.ConcertService;
import com.survive_the_traffic.seat.dto.SeatResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/concerts")
public class ConcertController {

    private final ConcertService concertService;

    @GetMapping
    public ResponseEntity<List<ConcertResponse>> getConcerts() {
        return ResponseEntity.ok(concertService.getConcerts());
    }

    @GetMapping("/{concertId}")
    public ResponseEntity<ConcertResponse> getConcertById(@PathVariable Long concertId) {
        return ResponseEntity.ok(concertService.getConcertById(concertId));
    }

    @GetMapping("/{concertId}/seats")
    public ResponseEntity<List<SeatResponse>> getConcertSeats(@PathVariable Long concertId) {
        return ResponseEntity.ok(concertService.getConcertSeats(concertId));
    }

    @PutMapping("/{concertId}")
    public ResponseEntity<ConcertResponse> updateConcert(
        @PathVariable Long concertId,
        @RequestBody ConcertUpdateRequest request
    ) {

        return ResponseEntity.ok(
            concertService.updateConcert(
                concertId,
                request
            )
        );
    }

}
