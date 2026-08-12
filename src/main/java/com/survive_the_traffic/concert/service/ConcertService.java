package com.survive_the_traffic.concert.service;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.concert.dto.ConcertResponse;
import com.survive_the_traffic.concert.exception.ConcertNotFoundException;
import com.survive_the_traffic.concert.repository.ConcertRepository;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.dto.SeatResponse;
import com.survive_the_traffic.seat.repository.SeatRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertService {

    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;

    public List<ConcertResponse> getConcerts() {
        return concertRepository.findAll().stream()
                .map(ConcertResponse::from)
                .toList();
    }

    public ConcertResponse getConcertById(Long concertId) {
        Concert concert = concertRepository.findById(concertId).orElseThrow(
            ConcertNotFoundException::new);
        return ConcertResponse.from(concert);
    }

    public List<SeatResponse> getConcertSeats(Long concertId) {

        if(!concertRepository.existsById(concertId)) {
            throw new ConcertNotFoundException();
        }
        return seatRepository.findAllByConcertId(concertId).stream()
                .map(SeatResponse::from)
                .toList();
    }
}
