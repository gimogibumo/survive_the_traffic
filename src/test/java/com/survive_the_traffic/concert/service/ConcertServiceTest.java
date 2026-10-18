package com.survive_the_traffic.concert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survive_the_traffic.concert.cache.ConcertCacheRepository;
import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.concert.dto.CacheStatus;
import com.survive_the_traffic.concert.dto.ConcertCacheResult;
import com.survive_the_traffic.concert.dto.ConcertResponse;
import com.survive_the_traffic.concert.exception.ConcertNotFoundException;
import com.survive_the_traffic.concert.repository.ConcertRepository;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.domain.SeatStatus;
import com.survive_the_traffic.seat.dto.SeatResponse;
import com.survive_the_traffic.seat.repository.SeatRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private ConcertCacheRepository concertCacheRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ConcertService concertService;


    @Test
    @DisplayName("전체 공연 목록을 조회한다")
    void getConcerts_success() {

        // given
        Concert concert1 =
            mock(Concert.class);

        Concert concert2 =
            mock(Concert.class);

        LocalDateTime openAt =
            LocalDateTime.of(
                2026,
                9,
                1,
                20,
                0
            );

        LocalDateTime concertAt =
            LocalDateTime.of(
                2026,
                9,
                30,
                19,
                0
            );

        when(
            concert1.getId()
        ).thenReturn(
            1L
        );

        when(
            concert1.getTitle()
        ).thenReturn(
            "Backend Festival"
        );

        when(
            concert1.getReservationOpenAt()
        ).thenReturn(
            openAt
        );

        when(
            concert1.getConcertAt()
        ).thenReturn(
            concertAt
        );


        when(
            concert2.getId()
        ).thenReturn(
            2L
        );

        when(
            concert2.getTitle()
        ).thenReturn(
            "Spring Festival"
        );

        when(
            concert2.getReservationOpenAt()
        ).thenReturn(
            openAt
        );

        when(
            concert2.getConcertAt()
        ).thenReturn(
            concertAt
        );


        when(
            concertRepository.findAll()
        ).thenReturn(
            List.of(
                concert1,
                concert2
            )
        );


        // when
        List<ConcertResponse> result =
            concertService.getConcerts();


        // then
        assertEquals(
            2,
            result.size()
        );

        assertEquals(
            1L,
            result.get(0).id()
        );

        assertEquals(
            "Backend Festival",
            result.get(0).title()
        );

        assertEquals(
            2L,
            result.get(1).id()
        );

        assertEquals(
            "Spring Festival",
            result.get(1).title()
        );

        verify(
            concertRepository
        ).findAll();
    }


    @Test
    @DisplayName("캐시에 없는 공연은 DB에서 조회하고 캐시에 저장한다")
    void getConcertById_success() {

        // given
        Long concertId =
            1L;

        LocalDateTime openAt =
            LocalDateTime.of(
                2026,
                9,
                1,
                20,
                0
            );

        LocalDateTime concertAt =
            LocalDateTime.of(
                2026,
                9,
                30,
                19,
                0
            );

        Concert concert =
            mock(Concert.class);

        ConcertCacheResult cacheMissResult =
            cacheMissResult();


        /*
         * ConcertService는 MISS인 경우 캐시를 두 번 조회한다.
         *
         * 1차 조회:
         * 일반 cache lookup
         *
         * 2차 조회:
         * synchronized 진입 후 stampede 방지를 위한 double-check
         */
        when(
            concertCacheRepository.findById(
                concertId
            )
        ).thenReturn(
            cacheMissResult,
            cacheMissResult
        );


        when(
            concertRepository.findById(
                concertId
            )
        ).thenReturn(
            Optional.of(concert)
        );


        when(
            concert.getId()
        ).thenReturn(
            concertId
        );

        when(
            concert.getTitle()
        ).thenReturn(
            "Backend Festival"
        );

        when(
            concert.getReservationOpenAt()
        ).thenReturn(
            openAt
        );

        when(
            concert.getConcertAt()
        ).thenReturn(
            concertAt
        );


        // when
        ConcertResponse result =
            concertService.getConcertById(
                concertId
            );


        // then
        assertEquals(
            concertId,
            result.id()
        );

        assertEquals(
            "Backend Festival",
            result.title()
        );


        /*
         * MISS -> synchronized -> MISS
         *
         * 따라서 캐시 조회는 정확히 두 번 일어나야 한다.
         */
        verify(
            concertCacheRepository,
            times(2)
        ).findById(
            concertId
        );


        /*
         * 두 번째 캐시 확인에서도 MISS였기 때문에
         * 실제 DB를 한 번 조회한다.
         */
        verify(
            concertRepository
        ).findById(
            concertId
        );


        /*
         * DB에서 조회한 결과를 cache-aside 방식으로 Redis에 저장한다.
         *
         * ConcertService가 반환한 result와
         * cache에 저장한 DTO는 같은 객체다.
         */
        verify(
            concertCacheRepository
        ).save(
            concertId,
            result
        );
    }


    @Test
    @DisplayName("캐시와 DB 모두에 존재하지 않는 공연은 negative cache를 저장하고 예외를 발생시킨다")
    void getConcertById_notFound() {

        // given
        Long concertId =
            999L;

        ConcertCacheResult cacheMissResult =
            cacheMissResult();


        /*
         * 첫 번째 cache lookup도 MISS,
         * synchronized 이후 두 번째 lookup도 MISS.
         */
        when(
            concertCacheRepository.findById(
                concertId
            )
        ).thenReturn(
            cacheMissResult,
            cacheMissResult
        );


        when(
            concertRepository.findById(
                concertId
            )
        ).thenReturn(
            Optional.empty()
        );


        // when & then
        assertThrows(
            ConcertNotFoundException.class,
            () ->
                concertService.getConcertById(
                    concertId
                )
        );


        /*
         * cache stampede 방지를 위한 double-check 때문에
         * 캐시는 두 번 조회된다.
         */
        verify(
            concertCacheRepository,
            times(2)
        ).findById(
            concertId
        );


        /*
         * 두 번 모두 MISS였기 때문에 실제 DB 조회까지 수행한다.
         */
        verify(
            concertRepository
        ).findById(
            concertId
        );


        /*
         * DB에도 존재하지 않으므로 동일한 잘못된 ID 요청이
         * 계속 DB까지 침투하지 않도록 negative cache를 저장한다.
         */
        verify(
            concertCacheRepository
        ).saveNotFound(
            concertId
        );
    }


    @Test
    @DisplayName("공연의 좌석 목록을 조회한다")
    void getConcertSeats_success() {

        // given
        Long concertId =
            1L;

        Seat seat1 =
            mock(Seat.class);

        Seat seat2 =
            mock(Seat.class);


        when(
            seat1.getId()
        ).thenReturn(
            101L
        );

        when(
            seat1.getSection()
        ).thenReturn(
            "A"
        );

        when(
            seat1.getRowNumber()
        ).thenReturn(
            1
        );

        when(
            seat1.getSeatNumber()
        ).thenReturn(
            1
        );

        when(
            seat1.getPrice()
        ).thenReturn(
            150000L
        );

        when(
            seat1.getStatus()
        ).thenReturn(
            SeatStatus.AVAILABLE
        );


        when(
            seat2.getId()
        ).thenReturn(
            102L
        );

        when(
            seat2.getSection()
        ).thenReturn(
            "A"
        );

        when(
            seat2.getRowNumber()
        ).thenReturn(
            1
        );

        when(
            seat2.getSeatNumber()
        ).thenReturn(
            2
        );

        when(
            seat2.getPrice()
        ).thenReturn(
            150000L
        );

        when(
            seat2.getStatus()
        ).thenReturn(
            SeatStatus.AVAILABLE
        );


        when(
            concertRepository.existsById(
                concertId
            )
        ).thenReturn(
            true
        );


        when(
            seatRepository.findAllByConcertId(
                concertId
            )
        ).thenReturn(
            List.of(
                seat1,
                seat2
            )
        );


        // when
        List<SeatResponse> result =
            concertService.getConcertSeats(
                concertId
            );


        // then
        assertEquals(
            2,
            result.size()
        );

        assertEquals(
            101L,
            result.getFirst().seatId()
        );

        assertEquals(
            "A",
            result.getFirst().section()
        );


        verify(
            concertRepository
        ).existsById(
            concertId
        );

        verify(
            seatRepository
        ).findAllByConcertId(
            concertId
        );
    }


    /**
     * ConcertService에서
     *
     * HIT       -> cache hit
     * NOT_FOUND -> negative cache hit
     * 그 외      -> cache miss
     *
     * 로 처리하고 있다.
     *
     * CacheStatus enum의 실제 MISS 상수명을 테스트 코드에서
     * 임의로 가정하지 않기 위해 HIT과 NOT_FOUND가 아닌
     * 상태를 찾아 사용한다.
     */
    private ConcertCacheResult cacheMissResult() {

        CacheStatus missStatus =
            Arrays.stream(
                    CacheStatus.values()
                )
                .filter(
                    status ->
                        status
                            != CacheStatus.HIT
                )
                .filter(
                    status ->
                        status
                            != CacheStatus.NOT_FOUND
                )
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "CacheStatus에 cache miss를 표현하는 상태가 없습니다."
                        )
                );


        return new ConcertCacheResult(
            missStatus,
            null
        );
    }
}