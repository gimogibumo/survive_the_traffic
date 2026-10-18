package com.survive_the_traffic.reservation.service;

import com.survive_the_traffic.common.exception.AdmissionRequiredException;
import com.survive_the_traffic.queue.service.QueueAdmissionService;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import org.springframework.stereotype.Component;

@Component
public class ReservationFacade {

    private final QueueAdmissionService queueAdmissionService;

    private final ReservationService reservationService;


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


        /*
         * Queue admission은 일회성 reservation token이 아니다.
         *
         * 사용자는 admission 만료 시간 전까지
         * 해당 concert의 reservation 영역에 접근할 수 있다.
         *
         * 따라서 여기서는 admission 상태를 확인만 한다.
         *
         * ADMITTED -> RESERVING 같은 상태 전이는 하지 않는다.
         * Reservation 성공 이후 admission을 소비하지도 않는다.
         */
        boolean admitted =
            queueAdmissionService
                .isAdmitted(
                    concertId,
                    userId
                );


        if (!admitted) {

            throw new AdmissionRequiredException();
        }


        /*
         * admission이 유효하면 실제 reservation 로직을 수행한다.
         *
         * ReservationService 내부의 DB transaction 성공/실패와
         * Redis admission lifecycle은 서로 독립적이다.
         *
         * 따라서 reservation이 실패하더라도 admission을
         * restore할 필요가 없다.
         */
        return reservationService
            .createReservation(
                request
            );
    }
}