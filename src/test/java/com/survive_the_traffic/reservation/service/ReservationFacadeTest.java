package com.survive_the_traffic.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.survive_the_traffic.common.exception.AdmissionRequiredException;
import com.survive_the_traffic.queue.service.QueueAdmissionService;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationFacadeTest {

    @Mock
    QueueAdmissionService queueAdmissionService;

    @Mock
    ReservationService reservationService;

    @InjectMocks
    ReservationFacade reservationFacade;


    @Test
    void admitted_사용자는_예약을_진행한다() {

        //given
        ReservationRequest request =
            new ReservationRequest(
                1L,
                10L,
                100L
            );

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        ReservationResponse response = new ReservationResponse(
            1L,
            100L,
            ReservationStatus.PENDING,
            expiresAt
        );

        given(
            queueAdmissionService.isAdmitted(1L, 10L)
        ).willReturn(true);

        given(
            reservationService.createReservation(request)
        ).willReturn(response);

        // when
        ReservationResponse result =
            reservationFacade.createReservation(request);

        // then
        assertThat(result).isEqualTo(response);

        then(queueAdmissionService)
            .should()
            .isAdmitted(1L, 10L);

        then(reservationService)
            .should()
            .createReservation(request);
    }

    @Test
    void admitted되지_않은_사용자는_예약을_진행할_수_없다() {

        //given
        ReservationRequest request =
            new ReservationRequest(
                1L,
                10L,
                100L
            );
        given(
            queueAdmissionService.isAdmitted(1L, 10L)
        ).willReturn(false);

        // when & then
        assertThatThrownBy(
            () -> reservationFacade.createReservation(request)
        )
            .isInstanceOf(AdmissionRequiredException.class);

        then(queueAdmissionService)
            .should()
            .isAdmitted(1L, 10L);

        then(reservationService)
            .shouldHaveNoInteractions();
    }


}