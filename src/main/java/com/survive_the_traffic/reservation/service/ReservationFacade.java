package com.survive_the_traffic.reservation.service;

import com.survive_the_traffic.common.exception.AdmissionRequiredException;
import com.survive_the_traffic.common.exception.ReservationAlreadyInProgressException;
import com.survive_the_traffic.queue.dto.AdmissionClaimResult;
import com.survive_the_traffic.queue.entity.AdmissionClaimStatus;
import com.survive_the_traffic.queue.entity.AdmissionRestoreStatus;
import com.survive_the_traffic.queue.repository.QueueAdmissionRepository;
import com.survive_the_traffic.queue.service.QueueAdmissionService;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
public class ReservationFacade {

    private static final Logger log =
        LoggerFactory.getLogger(
            ReservationFacade.class
        );


    private final QueueAdmissionService
        queueAdmissionService;

    private final ReservationService
        reservationService;


    public ReservationFacade(
        QueueAdmissionService queueAdmissionService,
        ReservationService reservationService
    ) {

        this.queueAdmissionService =
            queueAdmissionService;

        this.reservationService =
            reservationService;
    }


    public ReservationResponse createReservation(
        ReservationRequest request
    ) {

        Long concertId =
            request.concertId();

        Long userId =
            request.userId();


        // ====================================================
        // 1. admission atomic claim
        //
        // 기존 isAdmitted() check와 달리
        // 확인과 점유를 하나의 Redis Lua에서 처리한다.
        //
        // ADMITTED -> RESERVING
        // ====================================================

        AdmissionClaimResult claimResult =
            queueAdmissionService
                .claim(
                    concertId,
                    userId
                );


        // ====================================================
        // 2. admission 자체가 없음
        // ====================================================

        if (
            claimResult.status()
                == AdmissionClaimStatus
                .NOT_ADMITTED
        ) {

            throw new AdmissionRequiredException();
        }


        // ====================================================
        // 3. 이미 다른 요청이 동일 admission을 사용 중
        // ====================================================

        if (
            claimResult.status()
                == AdmissionClaimStatus
                .ALREADY_RESERVING
        ) {

            throw new ReservationAlreadyInProgressException();
        }


        // ====================================================
        // 4. 여기부터는 CLAIMED 상태만 도달해야 한다.
        // ====================================================

        if (
            claimResult.status()
                != AdmissionClaimStatus
                .CLAIMED
        ) {

            throw new IllegalStateException(
                "Unexpected admission claim status: "
                    + claimResult.status()
            );
        }


        ReservationResponse response;


        // ====================================================
        // 5. 실제 DB Reservation 수행
        //
        // ReservationService가 별도의 Spring @Transactional
        // Bean이라면 method 반환 시 transaction commit까지 끝난다.
        //
        // 실패하면 catch에서 admission을 복구한다.
        // ====================================================

        try {

            response =
                reservationService
                    .createReservation(
                        request
                    );

        } catch (RuntimeException reservationException) {

            restoreAdmissionSafely(
                concertId,
                userId,
                reservationException
            );

            throw reservationException;
        }


        // ====================================================
        // 6. DB Reservation 성공
        //
        // admission을 최종 소비한다.
        //
        // 여기서 Redis complete가 실패하더라도 이미 DB 예약은
        // 성공했으므로 사용자에게 예약 실패를 반환하면 안 된다.
        //
        // 따라서 complete 오류는 로그만 남긴다.
        // RESERVING 상태가 남는 쪽이 admission을 재사용 가능하게
        // 만드는 것보다 안전한 failure mode다.
        // ====================================================

        completeAdmissionSafely(
            concertId,
            userId
        );


        return response;
    }


    private void completeAdmissionSafely(
        Long concertId,
        Long userId
    ) {

        try {

            boolean completed =
                queueAdmissionService
                    .complete(
                        concertId,
                        userId
                    );


            if (!completed) {

                log.error(
                    "Reservation succeeded but admission completion "
                        + "did not remove RESERVING state. "
                        + "concertId={}, userId={}",
                    concertId,
                    userId
                );
            }

        } catch (RuntimeException completeException) {

            log.error(
                "Reservation succeeded but admission completion failed. "
                    + "concertId={}, userId={}",
                concertId,
                userId,
                completeException
            );
        }
    }


    private void restoreAdmissionSafely(
        Long concertId,
        Long userId,
        RuntimeException reservationException
    ) {

        try {

            AdmissionRestoreStatus restoreStatus =
                queueAdmissionService
                    .restore(
                        concertId,
                        userId
                    );


            if (
                restoreStatus
                    == AdmissionRestoreStatus
                    .RESTORED
            ) {

                log.debug(
                    "Admission restored after reservation failure. "
                        + "concertId={}, userId={}",
                    concertId,
                    userId
                );

                return;
            }


            if (
                restoreStatus
                    == AdmissionRestoreStatus
                    .EXPIRED
            ) {

                log.warn(
                    "Reservation failed and admission expired before restore. "
                        + "concertId={}, userId={}",
                    concertId,
                    userId
                );

                return;
            }


            if (
                restoreStatus
                    == AdmissionRestoreStatus
                    .NOT_RESERVING
            ) {

                log.error(
                    "Reservation failed but admission was not in "
                        + "RESERVING state. concertId={}, userId={}",
                    concertId,
                    userId
                );
            }

        } catch (RuntimeException restoreException) {

            /*
             * restore 실패가 원래 Reservation 실패 원인을
             * 덮어쓰면 안 된다.
             */

            log.error(
                "Failed to restore admission after reservation failure. "
                    + "concertId={}, userId={}, "
                    + "reservationException={}",
                concertId,
                userId,
                reservationException.getClass()
                    .getSimpleName(),
                restoreException
            );
        }
    }
}