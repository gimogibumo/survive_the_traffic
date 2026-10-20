package com.survive_the_traffic.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survive_the_traffic.auth.security.AuthenticatedUser;
import com.survive_the_traffic.common.config.SecurityConfig;
import com.survive_the_traffic.payment.domain.PaymentStatus;
import com.survive_the_traffic.payment.dto.PaymentResponse;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.reservation.dto.ReservationDetailResponse;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import com.survive_the_traffic.reservation.service.ReservationFacade;
import com.survive_the_traffic.reservation.service.ReservationService;
import com.survive_the_traffic.user.domain.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    ReservationController.class
)
@Import(
    SecurityConfig.class
)
class ReservationControllerTest {

    private static final Long USER_ID =
        10L;

    private static final Long CONCERT_ID =
        1L;

    private static final Long SEAT_ID =
        100L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationFacade reservationFacade;

    @MockitoBean
    private ReservationService reservationService;


    @Test
    void 인증된_사용자는_예약을_생성할_수_있다()
        throws Exception {

        // given
        LocalDateTime expiresAt =
            LocalDateTime.of(
                2026,
                10,
                19,
                15,
                30
            );

        ReservationResponse response =
            new ReservationResponse(
                1L,
                SEAT_ID,
                ReservationStatus.PENDING,
                expiresAt
            );

        given(
            reservationFacade
                .createReservation(
                    eq(USER_ID),
                    any(
                        ReservationRequest.class
                    )
                )
        ).willReturn(
            response
        );


        // when + then
        mockMvc.perform(
                post(
                    "/api/reservations"
                )
                    .with(
                        user(
                            authenticatedUser()
                        )
                    )
                    .with(
                        csrf()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "concertId": 1,
                          "seatId": 100
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.reservationId"
                ).value(
                    1L
                )
            )
            .andExpect(
                jsonPath(
                    "$.seatId"
                ).value(
                    100L
                )
            )
            .andExpect(
                jsonPath(
                    "$.status"
                ).value(
                    "PENDING"
                )
            );


        then(
            reservationFacade
        )
            .should()
            .createReservation(
                eq(USER_ID),
                eq(
                    new ReservationRequest(
                        CONCERT_ID,
                        SEAT_ID
                    )
                )
            );
    }


    @Test
    void request_body의_userId가_아니라_인증된_사용자_id를_사용한다()
        throws Exception {

        // given
        ReservationResponse response =
            new ReservationResponse(
                1L,
                SEAT_ID,
                ReservationStatus.PENDING,
                LocalDateTime.of(
                    2026,
                    10,
                    19,
                    15,
                    30
                )
            );

        given(
            reservationFacade
                .createReservation(
                    eq(USER_ID),
                    any(
                        ReservationRequest.class
                    )
                )
        ).willReturn(
            response
        );


        /*
         * 악의적으로 userId=999를 body에 넣는다.
         *
         * ReservationRequest에는 userId field가 없으므로
         * business identity는 반드시 principal의 USER_ID=10이어야 한다.
         */
        mockMvc.perform(
                post(
                    "/api/reservations"
                )
                    .with(
                        user(
                            authenticatedUser()
                        )
                    )
                    .with(
                        csrf()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "concertId": 1,
                          "userId": 999,
                          "seatId": 100
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            );


        /*
         * userId=999가 아니라
         * authenticated principal의 userId=10이 전달되는지 검증.
         */
        then(
            reservationFacade
        )
            .should()
            .createReservation(
                eq(USER_ID),
                eq(
                    new ReservationRequest(
                        CONCERT_ID,
                        SEAT_ID
                    )
                )
            );
    }


    @Test
    void 인증되지_않은_사용자는_예약할_수_없다()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/reservations"
                )
                    .with(
                        csrf()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "concertId": 1,
                          "seatId": 100
                        }
                        """
                    )
            )
            .andExpect(
                status().isUnauthorized()
            );


        then(
            reservationFacade
        )
            .shouldHaveNoInteractions();
    }


    @Test
    void CSRF_토큰이_없으면_예약요청은_403이다()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/reservations"
                )
                    .with(
                        user(
                            authenticatedUser()
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "concertId": 1,
                          "seatId": 100
                        }
                        """
                    )
            )
            .andExpect(
                status().isForbidden()
            );


        then(
            reservationFacade
        )
            .shouldHaveNoInteractions();
    }

    @Test
    void 결제는_인증된_사용자_id를_사용한다() throws Exception {

        // given
        Long reservationId = 1000L;

        PaymentResponse response =
            new PaymentResponse(
                5000L,
                reservationId,
                PaymentStatus.SUCCESS,
                150000L
            );

        given(
            reservationService.createPayment(
                USER_ID,
                reservationId
            )
        ).willReturn(response);


        // when + then
        mockMvc.perform(
                post(
                    "/api/reservations/{reservationId}/payments",
                    reservationId
                )
                    .with(user(authenticatedUser()))
                    .with(csrf())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentId").value(5000))
            .andExpect(jsonPath("$.reservationId").value(reservationId))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.amount").value(150000));


        then(
            reservationService
        )
            .should()
            .createPayment(
                USER_ID,
                reservationId
            );
    }

    @Test
    void 인증되지_않은_사용자는_결제할_수_없다() throws Exception {

        Long reservationId = 1000L;

        mockMvc.perform(
                post(
                    "/api/reservations/{reservationId}/payments",
                    reservationId
                )
                    .with(csrf())
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void CSRF_토큰이_없으면_결제할_수_없다() throws Exception {

        Long reservationId = 1000L;

        mockMvc.perform(
                post(
                    "/api/reservations/{reservationId}/payments",
                    reservationId
                )
                    .with(user(authenticatedUser()))
            )
            .andExpect(status().isForbidden());
    }


    private AuthenticatedUser authenticatedUser() {

        User user =
            User.builder()
                .id(
                    USER_ID
                )
                .email(
                    "kim@example.com"
                )
                .name(
                    "기범"
                )
                .passwordHash(
                    "encoded-password"
                )
                .createdAt(
                    LocalDateTime.of(
                        2026,
                        10,
                        19,
                        10,
                        0
                    )
                )
                .build();

        return AuthenticatedUser.from(
            user
        );
    }

    @Test
    void 예약_상세_조회는_인증된_사용자_id를_사용한다()
        throws Exception {

        // given
        Long reservationId =
            1000L;

        LocalDateTime expiresAt =
            LocalDateTime.of(
                2026,
                10,
                20,
                14,
                30
            );

        ReservationDetailResponse response =
            new ReservationDetailResponse(
                reservationId,
                "Survive the Traffic Live",
                "A",
                3,
                12,
                150000L,
                ReservationStatus.PENDING,
                expiresAt
            );

        given(
            reservationService
                .getReservation(
                    USER_ID,
                    reservationId
                )
        ).willReturn(
            response
        );


        // when + then
        mockMvc.perform(
                get(
                    "/api/reservations/{reservationId}",
                    reservationId
                )
                    .with(
                        user(
                            authenticatedUser()
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.reservationId")
                    .value(reservationId)
            )
            .andExpect(
                jsonPath("$.concertTitle")
                    .value(
                        "Survive the Traffic Live"
                    )
            )
            .andExpect(
                jsonPath("$.section")
                    .value("A")
            )
            .andExpect(
                jsonPath("$.row")
                    .value(3)
            )
            .andExpect(
                jsonPath("$.seatNumber")
                    .value(12)
            )
            .andExpect(
                jsonPath("$.price")
                    .value(150000L)
            )
            .andExpect(
                jsonPath("$.status")
                    .value("PENDING")
            );

        then(
            reservationService
        )
            .should()
            .getReservation(
                USER_ID,
                reservationId
            );
    }

    @Test
    void 인증되지_않은_사용자는_예약_상세를_조회할_수_없다()
        throws Exception {

        Long reservationId =
            1000L;

        mockMvc.perform(
                get(
                    "/api/reservations/{reservationId}",
                    reservationId
                )
            )
            .andExpect(
                status().isUnauthorized()
            );
    }
}