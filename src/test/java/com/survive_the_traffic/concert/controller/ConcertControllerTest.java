package com.survive_the_traffic.concert.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survive_the_traffic.common.config.SecurityConfig;
import com.survive_the_traffic.concert.dto.ConcertResponse;
import com.survive_the_traffic.concert.exception.ConcertNotFoundException;
import com.survive_the_traffic.concert.service.ConcertService;
import com.survive_the_traffic.seat.domain.SeatStatus;
import com.survive_the_traffic.seat.dto.SeatResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConcertController.class)
@Import(SecurityConfig.class)
class ConcertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConcertService concertService;

    @Test
    @DisplayName("공연 목록을 조회한다")
    void getConcerts_success() throws Exception {

        // given
        LocalDateTime reservationOpenAt =
            LocalDateTime.of(2026, 9, 1, 20, 0);

        LocalDateTime concertAt =
            LocalDateTime.of(2026, 9, 30, 19, 0);

        ConcertResponse concert1 = new ConcertResponse(
            1L,
            "Backend Festival",
            reservationOpenAt,
            concertAt
        );

        ConcertResponse concert2 = new ConcertResponse(
            2L,
            "Spring Festival",
            reservationOpenAt,
            concertAt
        );

        when(concertService.getConcerts())
            .thenReturn(List.of(concert1, concert2));


        // when & then
        mockMvc.perform(
                get("/api/concerts")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].title")
                .value("Backend Festival"))
            .andExpect(jsonPath("$[1].id").value(2))
            .andExpect(jsonPath("$[1].title")
                .value("Spring Festival"));
    }

    @Test
    @DisplayName("ID로 공연을 조회한다")
    void getConcertById_success() throws Exception {

        // given
        Long concertId = 1L;

        ConcertResponse response = new ConcertResponse(
            1L,
            "Backend Festival",
            LocalDateTime.of(2026, 9, 1, 20, 0),
            LocalDateTime.of(2026, 9, 30, 19, 0)
        );

        when(concertService.getConcertById(concertId))
            .thenReturn(response);


        // when & then
        mockMvc.perform(
                get("/api/concerts/{concertId}", concertId)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.title")
                .value("Backend Festival"))
            .andExpect(jsonPath("$.reservationOpenAt")
                .value("2026-09-01T20:00:00"))
            .andExpect(jsonPath("$.concertAt")
                .value("2026-09-30T19:00:00"));
    }

    @Test
    @DisplayName("존재하지 않는 공연을 조회하면 404를 반환한다")
    void getConcertById_notFound() throws Exception {

        // given
        Long concertId = 999L;

        when(concertService.getConcertById(concertId))
            .thenThrow(new ConcertNotFoundException());


        // when & then
        mockMvc.perform(
                get("/api/concerts/{concertId}", concertId)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value("CONCERT_NOT_FOUND"))
            .andExpect(jsonPath("$.message")
                .value("콘서트를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("공연의 좌석 목록을 조회한다")
    void getConcertSeats_success() throws Exception {

        // given
        Long concertId = 1L;

        SeatResponse seat1 = new SeatResponse(
            101L,
            "A",
            1,
            1,
            150000L,
            SeatStatus.AVAILABLE
        );

        SeatResponse seat2 = new SeatResponse(
            102L,
            "A",
            1,
            2,
            150000L,
            SeatStatus.AVAILABLE
        );

        when(concertService.getConcertSeats(concertId))
            .thenReturn(List.of(seat1, seat2));


        // when & then
        mockMvc.perform(
                get(
                    "/api/concerts/{concertId}/seats",
                    concertId
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))

            .andExpect(jsonPath("$[0].seatId").value(101))
            .andExpect(jsonPath("$[0].section").value("A"))
            .andExpect(jsonPath("$[0].row").value(1))
            .andExpect(jsonPath("$[0].number").value(1))
            .andExpect(jsonPath("$[0].price").value(150000))
            .andExpect(jsonPath("$[0].status")
                .value("AVAILABLE"))

            .andExpect(jsonPath("$[1].seatId").value(102));
    }

    @Test
    @DisplayName("존재하지 않는 공연의 좌석을 조회하면 404를 반환한다")
    void getConcertSeats_notFound() throws Exception {

        // given
        Long concertId = 999L;

        when(concertService.getConcertSeats(concertId))
            .thenThrow(new ConcertNotFoundException());


        // when & then
        mockMvc.perform(
                get(
                    "/api/concerts/{concertId}/seats",
                    concertId
                )
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value("CONCERT_NOT_FOUND"))
            .andExpect(jsonPath("$.message")
                .value("콘서트를 찾을 수 없습니다."));
    }
}