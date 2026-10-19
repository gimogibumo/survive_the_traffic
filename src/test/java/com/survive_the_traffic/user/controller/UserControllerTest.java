package com.survive_the_traffic.user.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.survive_the_traffic.common.config.SecurityConfig;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.reservation.dto.ReservationDetailResponse;
import com.survive_the_traffic.user.exception.UserNotFoundException;
import com.survive_the_traffic.user.service.UserService;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("사용자의 예약 목록을 조회한다")
    void getUserReservations_success() throws Exception {

        // given
        Long userId = 1L;

        ReservationDetailResponse reservation1 =
            new ReservationDetailResponse(
                1001L,
                "Backend Festival",
                "A",
                1,
                10,
                150000L,
                ReservationStatus.PENDING
            );

        ReservationDetailResponse reservation2 =
            new ReservationDetailResponse(
                1002L,
                "Spring Concert",
                "B",
                2,
                5,
                120000L,
                ReservationStatus.CONFIRMED
            );

        when(userService.getUserReservations(userId))
            .thenReturn(List.of(
                reservation1,
                reservation2
            ));


        // when & then
        mockMvc.perform(
                get(
                    "/api/users/{userId}/reservations",
                    userId
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))

            .andExpect(jsonPath("$[0].reservationId")
                .value(1001))
            .andExpect(jsonPath("$[0].concertTitle")
                .value("Backend Festival"))
            .andExpect(jsonPath("$[0].section")
                .value("A"))
            .andExpect(jsonPath("$[0].row")
                .value(1))
            .andExpect(jsonPath("$[0].seatNumber")
                .value(10))
            .andExpect(jsonPath("$[0].price")
                .value(150000))
            .andExpect(jsonPath("$[0].status")
                .value("PENDING"))

            .andExpect(jsonPath("$[1].reservationId")
                .value(1002))
            .andExpect(jsonPath("$[1].status")
                .value("CONFIRMED"));
    }

    @Test
    @DisplayName("예약이 없는 사용자는 빈 목록을 반환한다")
    void getUserReservations_empty() throws Exception {

        Long userId = 1L;

        when(userService.getUserReservations(userId))
            .thenReturn(List.of());

        mockMvc.perform(
                get(
                    "/api/users/{userId}/reservations",
                    userId
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 예약 목록 조회 시 404를 반환한다")
    void getUserReservations_userNotFound() throws Exception {

        Long userId = 999L;

        when(userService.getUserReservations(userId))
            .thenThrow(new UserNotFoundException());

        mockMvc.perform(
                get(
                    "/api/users/{userId}/reservations",
                    userId
                )
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value("USER_NOT_FOUND"));
    }
}