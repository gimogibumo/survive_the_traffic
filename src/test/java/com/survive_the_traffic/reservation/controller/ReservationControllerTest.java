package com.survive_the_traffic.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.survive_the_traffic.common.exception.AdmissionRequiredException;
import com.survive_the_traffic.payment.domain.PaymentStatus;
import com.survive_the_traffic.payment.dto.PaymentResponse;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import com.survive_the_traffic.reservation.exception.ReservationExpiredException;
import com.survive_the_traffic.reservation.exception.ReservationNotFoundException;
import com.survive_the_traffic.reservation.exception.ReservationNotPendingException;
import com.survive_the_traffic.reservation.service.ReservationFacade;
import com.survive_the_traffic.reservation.service.ReservationService;
import com.survive_the_traffic.seat.exception.SeatNotAvailableException;
import com.survive_the_traffic.seat.exception.SeatNotFoundException;
import com.survive_the_traffic.user.exception.UserNotFoundException;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private ReservationFacade reservationFacade;

    @Test
    void 예약을_생성한다() throws Exception {

        ReservationRequest request =
            new ReservationRequest(
                1L,
                10L,
                100L
            );

        ReservationResponse response =
            new ReservationResponse(
                1L,
                100L,
                ReservationStatus.PENDING,
                LocalDateTime.now().plusMinutes(5)
            );

        given(
            reservationFacade.createReservation(
                any(ReservationRequest.class)
            )
        ).willReturn(response);

        mockMvc.perform(
                post("/api/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "concertId": 1,
                          "userId": 10,
                          "seatId": 100
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reservationId").value(1))
            .andExpect(jsonPath("$.seatId").value(100));
    }

    @Test
    void admission되지_않은_사용자의_예약요청은_차단한다()
        throws Exception {

        given(
            reservationFacade.createReservation(
                any(ReservationRequest.class)
            )
        ).willThrow(
            new AdmissionRequiredException()
        );

        mockMvc.perform(
                post("/api/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                    {
                      "concertId": 1,
                      "userId": 10,
                      "seatId": 100
                    }
                    """)
            )
            .andExpect(
                status().isTooManyRequests()
            );
    }

    @Test
    @DisplayName("존재하지 않는 사용자가 예약하면 404를 반환한다")
    void createReservation_userNotFound() throws Exception {

        // given
        ReservationRequest request =
            new ReservationRequest(1L,999L, 101L);

        when(reservationFacade.createReservation(any(ReservationRequest.class)))
            .thenThrow(new UserNotFoundException());


        // when & then
        mockMvc.perform(
                post("/api/reservations")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하지 않는 좌석을 예약하면 404를 반환한다")
    void createReservation_seatNotFound() throws Exception {

        ReservationRequest request =
            new ReservationRequest(1L,1L, 999L);

        when(reservationFacade.createReservation(any(ReservationRequest.class)))
            .thenThrow(new SeatNotFoundException());

        mockMvc.perform(
                post("/api/reservations")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value("SEAT_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 예약된 좌석을 예약하면 409를 반환한다")
    void createReservation_seatNotAvailable() throws Exception {

        ReservationRequest request =
            new ReservationRequest(1L,1L, 101L);

        when(reservationFacade.createReservation(any(ReservationRequest.class)))
            .thenThrow(new SeatNotAvailableException());

        mockMvc.perform(
                post("/api/reservations")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value("SEAT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("예약 결제를 처리한다")
    void createPayment_success() throws Exception {

        // given
        Long reservationId = 1001L;

        PaymentResponse response =
            new PaymentResponse(
                5001L,
                1001L,
                PaymentStatus.SUCCESS,
                150000L
            );

        when(reservationService.createPayment(reservationId))
            .thenReturn(response);


        // when & then
        mockMvc.perform(
                post(
                    "/api/reservations/{reservationId}/payments",
                    reservationId
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentId").value(5001))
            .andExpect(jsonPath("$.reservationId").value(1001))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.amount").value(150000));
    }

    @Test
    @DisplayName("존재하지 않는 예약을 결제하면 404를 반환한다")
    void createPayment_reservationNotFound() throws Exception {

        Long reservationId = 999L;

        when(reservationService.createPayment(reservationId))
            .thenThrow(new ReservationNotFoundException());

        mockMvc.perform(
                post(
                    "/api/reservations/{reservationId}/payments",
                    reservationId
                )
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value("RESERVATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("PENDING 상태가 아닌 예약을 결제하면 409를 반환한다")
    void createPayment_notPending() throws Exception {

        Long reservationId = 1001L;

        when(reservationService.createPayment(reservationId))
            .thenThrow(new ReservationNotPendingException());

        mockMvc.perform(
                post(
                    "/api/reservations/{reservationId}/payments",
                    reservationId
                )
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value("RESERVATION_NOT_PENDING"));
    }

    @Test
    @DisplayName("만료된 예약을 결제하면 409를 반환한다")
    void createPayment_expired() throws Exception {

        Long reservationId = 1001L;

        when(reservationService.createPayment(reservationId))
            .thenThrow(new ReservationExpiredException());

        mockMvc.perform(
                post(
                    "/api/reservations/{reservationId}/payments",
                    reservationId
                )
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value("RESERVATION_EXPIRED"));
    }
}