package com.survive_the_traffic.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.survive_the_traffic.common.exception.AdmissionRequiredException;
import com.survive_the_traffic.queue.dto.AdmissionClaimResult;
import com.survive_the_traffic.queue.entity.AdmissionClaimStatus;
import com.survive_the_traffic.queue.entity.AdmissionRestoreStatus;
import com.survive_the_traffic.queue.service.QueueAdmissionService;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import com.survive_the_traffic.common.exception.ReservationAlreadyInProgressException;
import com.survive_the_traffic.reservation.service.ReservationService;

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
    void admitted_사용자는_claim후_예약할_수_있다() {

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
                .claim(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            new AdmissionClaimResult(
                AdmissionClaimStatus
                    .CLAIMED,
                System.currentTimeMillis()
                    + 300_000
            )
        );


        given(
            reservationService
                .createReservation(
                    request
                )
        ).willReturn(
            expectedResponse
        );


        given(
            queueAdmissionService
                .complete(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(true);


        // when
        ReservationResponse actualResponse =
            reservationFacade
                .createReservation(
                    request
                );


        // then
        assertThat(actualResponse)
            .isSameAs(
                expectedResponse
            );


        then(queueAdmissionService)
            .should()
            .claim(
                CONCERT_ID,
                USER_ID
            );


        then(reservationService)
            .should()
            .createReservation(
                request
            );


        then(queueAdmissionService)
            .should()
            .complete(
                CONCERT_ID,
                USER_ID
            );


        then(queueAdmissionService)
            .should(never())
            .restore(
                CONCERT_ID,
                USER_ID
            );
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
                .claim(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            new AdmissionClaimResult(
                AdmissionClaimStatus
                    .NOT_ADMITTED,
                null
            )
        );


        // when + then
        assertThatThrownBy(
            () -> reservationFacade
                .createReservation(
                    request
                )
        )
            .isInstanceOf(
                AdmissionRequiredException.class
            );


        then(reservationService)
            .shouldHaveNoInteractions();


        then(queueAdmissionService)
            .should(never())
            .complete(
                CONCERT_ID,
                USER_ID
            );


        then(queueAdmissionService)
            .should(never())
            .restore(
                CONCERT_ID,
                USER_ID
            );
    }


    @Test
    void 이미_다른_예약이_admission을_사용중이면_예약할_수_없다() {

        // given
        ReservationRequest request =
            new ReservationRequest(
                CONCERT_ID,
                USER_ID,
                100L
            );


        given(
            queueAdmissionService
                .claim(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            new AdmissionClaimResult(
                AdmissionClaimStatus
                    .ALREADY_RESERVING,
                System.currentTimeMillis()
                    + 300_000
            )
        );


        // when + then
        assertThatThrownBy(
            () -> reservationFacade
                .createReservation(
                    request
                )
        )
            .isInstanceOf(
                ReservationAlreadyInProgressException.class
            );


        then(reservationService)
            .shouldHaveNoInteractions();


        then(queueAdmissionService)
            .should(never())
            .complete(
                CONCERT_ID,
                USER_ID
            );


        then(queueAdmissionService)
            .should(never())
            .restore(
                CONCERT_ID,
                USER_ID
            );
    }


    @Test
    void reservation이_실패하면_admission을_복구하고_원래_예외를_다시_던진다() {

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
                .claim(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            new AdmissionClaimResult(
                AdmissionClaimStatus
                    .CLAIMED,
                System.currentTimeMillis()
                    + 300_000
            )
        );


        given(
            reservationService
                .createReservation(
                    request
                )
        ).willThrow(
            reservationException
        );


        given(
            queueAdmissionService
                .restore(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            AdmissionRestoreStatus
                .RESTORED
        );


        // when + then
        assertThatThrownBy(
            () -> reservationFacade
                .createReservation(
                    request
                )
        )
            .isSameAs(
                reservationException
            );


        then(queueAdmissionService)
            .should()
            .restore(
                CONCERT_ID,
                USER_ID
            );


        then(queueAdmissionService)
            .should(never())
            .complete(
                CONCERT_ID,
                USER_ID
            );
    }


    @Test
    void admission_restore가_실패해도_원래_reservation_예외를_유지한다() {

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


        RuntimeException restoreException =
            new RuntimeException(
                "redis restore failed"
            );


        given(
            queueAdmissionService
                .claim(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            new AdmissionClaimResult(
                AdmissionClaimStatus
                    .CLAIMED,
                System.currentTimeMillis()
                    + 300_000
            )
        );


        given(
            reservationService
                .createReservation(
                    request
                )
        ).willThrow(
            reservationException
        );


        given(
            queueAdmissionService
                .restore(
                    CONCERT_ID,
                    USER_ID
                )
        ).willThrow(
            restoreException
        );


        // when + then
        assertThatThrownBy(
            () -> reservationFacade
                .createReservation(
                    request
                )
        )
            .isSameAs(
                reservationException
            );


        then(queueAdmissionService)
            .should()
            .restore(
                CONCERT_ID,
                USER_ID
            );


        then(queueAdmissionService)
            .should(never())
            .complete(
                CONCERT_ID,
                USER_ID
            );
    }


    @Test
    void reservation_성공후_complete가_실패해도_reservation은_성공으로_반환한다() {

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
                .claim(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            new AdmissionClaimResult(
                AdmissionClaimStatus
                    .CLAIMED,
                System.currentTimeMillis()
                    + 300_000
            )
        );


        given(
            reservationService
                .createReservation(
                    request
                )
        ).willReturn(
            expectedResponse
        );


        given(
            queueAdmissionService
                .complete(
                    CONCERT_ID,
                    USER_ID
                )
        ).willThrow(
            new RuntimeException(
                "redis complete failed"
            )
        );


        // when
        ReservationResponse actualResponse =
            reservationFacade
                .createReservation(
                    request
                );


        // then
        assertThat(actualResponse)
            .isSameAs(
                expectedResponse
            );


        then(queueAdmissionService)
            .should()
            .complete(
                CONCERT_ID,
                USER_ID
            );


        then(queueAdmissionService)
            .should(never())
            .restore(
                CONCERT_ID,
                USER_ID
            );
    }


    @Test
    void 하나의_admission은_순차적으로_두_번_사용할_수_없다() {

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


        given(
            queueAdmissionService
                .claim(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(
            new AdmissionClaimResult(
                AdmissionClaimStatus
                    .CLAIMED,
                System.currentTimeMillis()
                    + 300_000
            ),
            new AdmissionClaimResult(
                AdmissionClaimStatus
                    .NOT_ADMITTED,
                null
            )
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
            queueAdmissionService
                .complete(
                    CONCERT_ID,
                    USER_ID
                )
        ).willReturn(true);


        // when
        ReservationResponse result =
            reservationFacade
                .createReservation(
                    firstRequest
                );


        // then - first request
        assertThat(result)
            .isSameAs(
                firstResponse
            );


        // when + then - second request
        assertThatThrownBy(
            () -> reservationFacade
                .createReservation(
                    secondRequest
                )
        )
            .isInstanceOf(
                AdmissionRequiredException.class
            );


        then(reservationService)
            .should(times(1))
            .createReservation(
                firstRequest
            );


        then(reservationService)
            .should(never())
            .createReservation(
                secondRequest
            );


        then(queueAdmissionService)
            .should(times(2))
            .claim(
                CONCERT_ID,
                USER_ID
            );


        then(queueAdmissionService)
            .should(times(1))
            .complete(
                CONCERT_ID,
                USER_ID
            );
    }
}