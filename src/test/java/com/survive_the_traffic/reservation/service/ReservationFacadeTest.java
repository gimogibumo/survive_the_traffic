package com.survive_the_traffic.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.survive_the_traffic.common.exception.AdmissionRequiredException;
import com.survive_the_traffic.queue.service.QueueAdmissionService;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(
    MockitoExtension.class
)
class ReservationFacadeTest {

    private static final Long CONCERT_ID =
        1L;

    private static final Long USER_ID =
        10L;

    private static final Long SEAT_ID =
        100L;


    @Mock
    private QueueAdmissionService
        queueAdmissionService;

    @Mock
    private ReservationService
        reservationService;

    @InjectMocks
    private ReservationFacade
        reservationFacade;


    @Test
    void admitted_사용자는_예약할_수_있다() {

        // given
        ReservationRequest request =
            new ReservationRequest(
                CONCERT_ID,
                SEAT_ID
            );

        ReservationResponse expectedResponse =
            mock(
                ReservationResponse.class
            );

        given(
            queueAdmissionService
                .isAdmitted(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            true
        );

        given(
            reservationService
                .createReservation(
                    USER_ID,
                    request
                )
        ).willReturn(
            expectedResponse
        );


        // when
        ReservationResponse actualResponse =
            reservationFacade
                .createReservation(
                    USER_ID,
                    request
                );


        // then
        assertThat(
            actualResponse
        ).isSameAs(
            expectedResponse
        );


        then(
            queueAdmissionService
        )
            .should()
            .isAdmitted(
                CONCERT_ID,
                USER_ID
            );


        then(
            reservationService
        )
            .should()
            .createReservation(
                USER_ID,
                request
            );
    }


    @Test
    void admitted되지_않은_사용자는_예약할_수_없다() {

        // given
        ReservationRequest request =
            new ReservationRequest(
                CONCERT_ID,
                SEAT_ID
            );

        given(
            queueAdmissionService
                .isAdmitted(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            false
        );


        // when + then
        assertThatThrownBy(
            () ->
                reservationFacade
                    .createReservation(
                        USER_ID,
                        request
                    )
        )
            .isInstanceOf(
                AdmissionRequiredException.class
            );


        then(
            queueAdmissionService
        )
            .should()
            .isAdmitted(
                CONCERT_ID,
                USER_ID
            );


        then(
            reservationService
        )
            .shouldHaveNoInteractions();
    }


    @Test
    void admission은_예약후에도_소비하지_않는다() {

        /*
         * 현재 ADMITTED의 의미는
         * "5분 동안 예약 영역 접근 가능"이다.
         *
         * 따라서 한 번 예약했다고 admission을
         * consume하면 안 된다.
         */

        ReservationRequest firstRequest =
            new ReservationRequest(
                CONCERT_ID,
                100L
            );

        ReservationRequest secondRequest =
            new ReservationRequest(
                CONCERT_ID,
                200L
            );

        ReservationResponse firstResponse =
            mock(
                ReservationResponse.class
            );

        ReservationResponse secondResponse =
            mock(
                ReservationResponse.class
            );


        given(
            queueAdmissionService
                .isAdmitted(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            true
        );


        given(
            reservationService
                .createReservation(
                    USER_ID,
                    firstRequest
                )
        ).willReturn(
            firstResponse
        );


        given(
            reservationService
                .createReservation(
                    USER_ID,
                    secondRequest
                )
        ).willReturn(
            secondResponse
        );


        // when
        ReservationResponse first =
            reservationFacade
                .createReservation(
                    USER_ID,
                    firstRequest
                );

        ReservationResponse second =
            reservationFacade
                .createReservation(
                    USER_ID,
                    secondRequest
                );


        // then
        assertThat(
            first
        ).isSameAs(
            firstResponse
        );

        assertThat(
            second
        ).isSameAs(
            secondResponse
        );


        then(
            queueAdmissionService
        )
            .should(
                org.mockito.Mockito.times(
                    2
                )
            )
            .isAdmitted(
                CONCERT_ID,
                USER_ID
            );


        then(
            reservationService
        )
            .should()
            .createReservation(
                USER_ID,
                firstRequest
            );


        then(
            reservationService
        )
            .should()
            .createReservation(
                USER_ID,
                secondRequest
            );
    }


    @Test
    void request에는_userId가_없고_별도_인증_identity를_사용한다() {

        // given
        ReservationRequest request =
            new ReservationRequest(
                CONCERT_ID,
                SEAT_ID
            );

        ReservationResponse expectedResponse =
            mock(
                ReservationResponse.class
            );

        given(
            queueAdmissionService
                .isAdmitted(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            true
        );

        given(
            reservationService
                .createReservation(
                    USER_ID,
                    request
                )
        ).willReturn(
            expectedResponse
        );


        // when
        reservationFacade
            .createReservation(
                USER_ID,
                request
            );


        // then
        then(
            reservationService
        )
            .should()
            .createReservation(
                USER_ID,
                new ReservationRequest(
                    CONCERT_ID,
                    SEAT_ID
                )
            );
    }
}