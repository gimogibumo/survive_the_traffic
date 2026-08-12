package com.survive_the_traffic.concert.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.concert.dto.ConcertResponse;
import com.survive_the_traffic.concert.exception.ConcertNotFoundException;
import com.survive_the_traffic.concert.repository.ConcertRepository;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.seat.domain.SeatStatus;
import com.survive_the_traffic.seat.dto.SeatResponse;
import com.survive_the_traffic.seat.repository.SeatRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private SeatRepository seatRepository;

    @InjectMocks
    private ConcertService concertService;

    @Test
    @DisplayName("전체 공연 목록을 조회한다")
    void getConcerts_success() {

        //given
        Concert concert1 = mock(Concert.class);
        Concert concert2 = mock(Concert.class);
        LocalDateTime openAt =
            LocalDateTime.of(2026, 9, 1, 20, 0);

        LocalDateTime concertAt =
            LocalDateTime.of(2026, 9, 30, 19, 0);

        when(concert1.getId()).thenReturn(1L);
        when(concert1.getTitle()).thenReturn("Backend Festival");
        when(concert1.getReservationOpenAt()).thenReturn(openAt);
        when(concert1.getConcertAt()).thenReturn(concertAt);

        when(concert2.getId()).thenReturn(2L);
        when(concert2.getTitle()).thenReturn("Spring Festival");
        when(concert2.getReservationOpenAt()).thenReturn(openAt);
        when(concert2.getConcertAt()).thenReturn(concertAt);

        when(concertRepository.findAll())
            .thenReturn(List.of(concert1, concert2));

        // when
        List<ConcertResponse> result =
            concertService.getConcerts();


        // then
        assertEquals(2, result.size());

        assertEquals(1L, result.get(0).id());
        assertEquals("Backend Festival", result.get(0).title());

        assertEquals(2L, result.get(1).id());
        assertEquals("Spring Festival", result.get(1).title());

        verify(concertRepository).findAll();
    }

    @Test
    @DisplayName("ID로 공연을 조회한다")
    void getConcertById_success() {

        //given
        Long concertId = 1L;

        Concert concert = mock(Concert.class);

        when(concert.getId()).thenReturn(concertId);
        when(concert.getTitle()).thenReturn("Backend Festival");
        when(concertRepository.findById(concertId)).thenReturn(Optional.of(concert));

        //when
        ConcertResponse result = concertService.getConcertById(concertId);

        //then
        assertEquals(concertId, result.id());
        assertEquals("Backend Festival", result.title());
    }

    @Test
    @DisplayName("존재하지 않는 공연을 조회하면 예외가 발생한다")
    void getConcertById_notFound() {

        //given
        Long concertId = 999L;

        when(concertRepository.findById(concertId)).thenReturn(Optional.empty());

        //when & then
        assertThrows(ConcertNotFoundException.class, () -> {
            concertService.getConcertById(concertId);
        });
    }

    @Test
    @DisplayName("공연의 좌석 목록을 조회한다")
    void getConcertSeats_success() {

        // given
        Long concertId = 1L;

        Seat seat1 = mock(Seat.class);
        Seat seat2 = mock(Seat.class);

        when(seat1.getId()).thenReturn(101L);
        when(seat1.getSection()).thenReturn("A");
        when(seat1.getRowNumber()).thenReturn(1);
        when(seat1.getSeatNumber()).thenReturn(1);
        when(seat1.getPrice()).thenReturn(150000L);
        when(seat1.getStatus()).thenReturn(SeatStatus.AVAILABLE);

        when(seat2.getId()).thenReturn(102L);
        when(seat2.getSection()).thenReturn("A");
        when(seat2.getRowNumber()).thenReturn(1);
        when(seat2.getSeatNumber()).thenReturn(2);
        when(seat2.getPrice()).thenReturn(150000L);
        when(seat2.getStatus()).thenReturn(SeatStatus.AVAILABLE);

        when(concertRepository.existsById(concertId))
            .thenReturn(true);

        when(seatRepository.findAllByConcertId(concertId))
            .thenReturn(List.of(seat1, seat2));

        // when
        List<SeatResponse> result =
            concertService.getConcertSeats(concertId);

        // then
        assertEquals(2, result.size());
        assertEquals(101L, result.getFirst().seatId());
        assertEquals("A", result.getFirst().section());

        verify(concertRepository).existsById(concertId);
        verify(seatRepository).findAllByConcertId(concertId);
    }
}