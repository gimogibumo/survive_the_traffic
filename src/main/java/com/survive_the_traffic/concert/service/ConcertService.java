package com.survive_the_traffic.concert.service;

import com.survive_the_traffic.concert.cache.ConcertCacheRepository;
import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.concert.dto.CacheStatus;
import com.survive_the_traffic.concert.dto.ConcertCacheResult;
import com.survive_the_traffic.concert.dto.ConcertResponse;
import com.survive_the_traffic.concert.dto.ConcertUpdateRequest;
import com.survive_the_traffic.concert.event.ConcertUpdatedEvent;
import com.survive_the_traffic.concert.exception.ConcertNotFoundException;
import com.survive_the_traffic.concert.repository.ConcertRepository;
import com.survive_the_traffic.seat.dto.SeatResponse;
import com.survive_the_traffic.seat.repository.SeatRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertService {

    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;
    private final ConcertCacheRepository concertCacheRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    public List<ConcertResponse> getConcerts() {
        return concertRepository.findAll().stream()
                .map(ConcertResponse::from)
                .toList();
    }

    public ConcertResponse getConcertById(Long concertId) {
        ConcertCacheResult cacheResult = concertCacheRepository.findById(concertId);

        if(cacheResult.cacheStatus() == CacheStatus.HIT) {
            return cacheResult.concert();
        }

        if(cacheResult.cacheStatus() == CacheStatus.NOT_FOUND) {
            throw new ConcertNotFoundException();
        }

        Object lock = locks.computeIfAbsent(concertId, key -> new Object());

        synchronized (lock) {
            ConcertCacheResult secondCheck = concertCacheRepository.findById(concertId);

            if(secondCheck.cacheStatus() == CacheStatus.HIT) {
                return secondCheck.concert();
            }

            if(secondCheck.cacheStatus() == CacheStatus.NOT_FOUND) {
                throw new ConcertNotFoundException();
            }
            // 여전히 없으면 이 Thread만 DB 조회
            Optional<Concert> concertOptional = concertRepository.findById(concertId);

            if(concertOptional.isEmpty()) {
                concertCacheRepository.saveNotFound(concertId);
                throw new ConcertNotFoundException();
            }
            Concert concert = concertOptional.get();

            ConcertResponse response =
                ConcertResponse.from(concert);

            concertCacheRepository.save(
                concertId,
                response
            );

            return response;
        }
    }

    public List<SeatResponse> getConcertSeats(Long concertId) {

        if(!concertRepository.existsById(concertId)) {
            throw new ConcertNotFoundException();
        }
        return seatRepository.findAllByConcertId(concertId).stream()
                .map(SeatResponse::from)
                .toList();
    }

    @Transactional
    public ConcertResponse updateConcert(
        Long concertId,
        ConcertUpdateRequest request
    ) {

        Concert concert = concertRepository.findById(concertId)
            .orElseThrow(ConcertNotFoundException::new);

        concert.update(
            request.title(),
            request.reservationOpenAt(),
            request.concertAt()
        );

        System.out.println("1. CONCERT UPDATED IN MEMORY");

        eventPublisher.publishEvent(new ConcertUpdatedEvent(concertId));

        System.out.println("2. EVENT PUBLISHED");

        return ConcertResponse.from(concert);
    }
}
