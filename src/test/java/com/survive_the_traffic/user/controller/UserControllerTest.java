package com.survive_the_traffic.user.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.survive_the_traffic.common.config.SecurityConfig;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.reservation.dto.ReservationDetailResponse;
import com.survive_the_traffic.user.domain.User;
import com.survive_the_traffic.user.exception.UserNotFoundException;
import com.survive_the_traffic.user.service.UserService;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.survive_the_traffic.auth.security.AuthenticatedUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("인증된 사용자의 예약 목록을 조회한다")
    void getMyReservations_success() throws Exception {

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

        when(
            userService.getUserReservations(USER_ID)
        ).thenReturn(
            List.of(
                reservation1,
                reservation2
            )
        );

        mockMvc.perform(
                get("/api/users/me/reservations")
                    .with(user(authenticatedUser()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].reservationId").value(1001))
            .andExpect(jsonPath("$[0].concertTitle").value("Backend Festival"))
            .andExpect(jsonPath("$[0].section").value("A"))
            .andExpect(jsonPath("$[0].row").value(1))
            .andExpect(jsonPath("$[0].seatNumber").value(10))
            .andExpect(jsonPath("$[0].price").value(150000))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
            .andExpect(jsonPath("$[1].reservationId").value(1002))
            .andExpect(jsonPath("$[1].status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("인증된 사용자의 예약이 없으면 빈 목록을 반환한다")
    void getMyReservations_empty() throws Exception {

        when(
            userService.getUserReservations(USER_ID)
        ).thenReturn(
            List.of()
        );

        mockMvc.perform(
                get("/api/users/me/reservations")
                    .with(user(authenticatedUser()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("인증된 사용자 정보가 존재하지 않으면 404를 반환한다")
    void getMyReservations_userNotFound() throws Exception {

        when(
            userService.getUserReservations(USER_ID)
        ).thenThrow(
            new UserNotFoundException()
        );

        mockMvc.perform(
                get("/api/users/me/reservations")
                    .with(user(authenticatedUser()))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 userId를 path로 지정하는 예약 조회 endpoint는 존재하지 않는다")
    void arbitraryUserIdReservationEndpoint_doesNotExist() throws Exception {

        mockMvc.perform(
                get("/api/users/999/reservations")
                    .with(user(authenticatedUser()))
            )
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 예약 목록을 조회할 수 없다")
    void anonymousCannotReadReservations() throws Exception {

        mockMvc.perform(
                get("/api/users/me/reservations")
            )
            .andExpect(status().isUnauthorized());
    }

    private AuthenticatedUser authenticatedUser() {

        User user = User.builder()
            .id(USER_ID)
            .email("kim@example.com")
            .name("기범")
            .passwordHash("encoded-password")
            .createdAt(LocalDateTime.of(2026, 10, 19, 10, 0))
            .build();

        return AuthenticatedUser.from(user);
    }
}