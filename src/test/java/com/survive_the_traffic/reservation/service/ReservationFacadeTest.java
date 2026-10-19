package com.survive_the_traffic.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.survive_the_traffic.common.exception.AdmissionRequiredException;
import com.survive_the_traffic.queue.service.QueueAdmissionService;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationFacadeTest {

    private static final Long CONCERT_ID =
        1L;

    private static final Long USER_ID =
        10L;


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
                USER_ID,
                100L
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
                    request
                )
        ).willReturn(
            expectedResponse
        );


        // when
        ReservationResponse actualResponse =
            reservationFacade
                .createReservation(
                    request
                );


        // then
        assertThat(
            actualResponse
        ).isSameAs(
            expectedResponse
        );


        then(queueAdmissionService)
            .should()
            .isAdmitted(
                CONCERT_ID,
                USER_ID
            );


        then(reservationService)
            .should()
            .createReservation(
                request
            );


        then(queueAdmissionService)
            .shouldHaveNoMoreInteractions();


        then(reservationService)
            .shouldHaveNoMoreInteractions();
    }


    @Test
    void admitted되지_않은_사용자는_예약할_수_없다() {

        // given
        ReservationRequest request =
            new ReservationRequest(
                CONCERT_ID,
                USER_ID,
                100L
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
                        request
                    )
        ).isInstanceOf(
            AdmissionRequiredException.class
        );


        then(queueAdmissionService)
            .should()
            .isAdmitted(
                CONCERT_ID,
                USER_ID
            );


        then(reservationService)
            .shouldHaveNoInteractions();


        then(queueAdmissionService)
            .shouldHaveNoMoreInteractions();
    }


    @Test
    void reservation이_실패해도_admission은_소비하거나_복구하지_않는다() {

        // given
        ReservationRequest request =
            new ReservationRequest(
                CONCERT_ID,
                USER_ID,
                100L
            );


        RuntimeException reservationException =
            new RuntimeException(
                "reservation failed"
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
                    request
                )
        ).willThrow(
            reservationException
        );


        // when + then
        assertThatThrownBy(
            () ->
                reservationFacade
                    .createReservation(
                        request
                    )
        ).isSameAs(
            reservationException
        );


        then(queueAdmissionService)
            .should()
            .isAdmitted(
                CONCERT_ID,
                USER_ID
            );


        then(reservationService)
            .should()
            .createReservation(
                request
            );


        /*
         * QueueAdmissionService에는 isAdmitted() 외에
         * claim / restore / complete 등이 아직 존재할 수 있다.
         *
         * shouldHaveNoMoreInteractions()를 통해
         * ReservationFacade가 그 상태 변경 API를
         * 전혀 호출하지 않는다는 것을 검증한다.
         */
        then(queueAdmissionService)
            .shouldHaveNoMoreInteractions();


        then(reservationService)
            .shouldHaveNoMoreInteractions();
    }


    @Test
    void 하나의_admission으로_만료전까지_여러_좌석을_순차적으로_예약할_수_있다() {

        // given
        ReservationRequest firstRequest =
            new ReservationRequest(
                CONCERT_ID,
                USER_ID,
                100L
            );


        ReservationRequest secondRequest =
            new ReservationRequest(
                CONCERT_ID,
                USER_ID,
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


        /*
         * 같은 admission window 안에서
         * 두 reservation request 모두 admission이 유효하다.
         */
        given(
            queueAdmissionService
                .isAdmitted(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            true,
            true
        );


        given(
            reservationService
                .createReservation(
                    firstRequest
                )
        ).willReturn(
            firstResponse
        );


        given(
            reservationService
                .createReservation(
                    secondRequest
                )
        ).willReturn(
            secondResponse
        );


        // when
        ReservationResponse firstResult =
            reservationFacade
                .createReservation(
                    firstRequest
                );


        ReservationResponse secondResult =
            reservationFacade
                .createReservation(
                    secondRequest
                );


        // then
        assertThat(
            firstResult
        ).isSameAs(
            firstResponse
        );


        assertThat(
            secondResult
        ).isSameAs(
            secondResponse
        );


        then(queueAdmissionService)
            .should(
                times(2)
            )
            .isAdmitted(
                CONCERT_ID,
                USER_ID
            );


        then(reservationService)
            .should()
            .createReservation(
                firstRequest
            );


        then(reservationService)
            .should()
            .createReservation(
                secondRequest
            );


        then(queueAdmissionService)
            .shouldHaveNoMoreInteractions();


        then(reservationService)
            .shouldHaveNoMoreInteractions();
    }


    @Test
    void 첫_예약후_admission이_만료되면_두번째_예약은_막힌다() {

        // given
        ReservationRequest firstRequest =
            new ReservationRequest(
                CONCERT_ID,
                USER_ID,
                100L
            );


        ReservationRequest secondRequest =
            new ReservationRequest(
                CONCERT_ID,
                USER_ID,
                200L
            );


        ReservationResponse firstResponse =
            mock(
                ReservationResponse.class
            );


        /*
         * 첫 번째 요청 시점에는 admission이 유효하지만,
         * 두 번째 요청 시점에는 만료됐다고 가정한다.
         */
        given(
            queueAdmissionService
                .isAdmitted(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            true,
            false
        );


        given(
            reservationService
                .createReservation(
                    firstRequest
                )
        ).willReturn(
            firstResponse
        );


        // when
        ReservationResponse firstResult =
            reservationFacade
                .createReservation(
                    firstRequest
                );


        // then
        assertThat(
            firstResult
        ).isSameAs(
            firstResponse
        );


        assertThatThrownBy(
            () ->
                reservationFacade
                    .createReservation(
                        secondRequest
                    )
        ).isInstanceOf(
            AdmissionRequiredException.class
        );


        then(queueAdmissionService)
            .should(
                times(2)
            )
            .isAdmitted(
                CONCERT_ID,
                USER_ID
            );


        then(reservationService)
            .should()
            .createReservation(
                firstRequest
            );


        then(reservationService)
            .shouldHaveNoMoreInteractions();


        then(queueAdmissionService)
            .shouldHaveNoMoreInteractions();
    }
}