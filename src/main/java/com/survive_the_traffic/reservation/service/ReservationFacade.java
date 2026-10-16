package com.survive_the_traffic.reservation.service;

import com.survive_the_traffic.common.exception.AdmissionRequiredException;
import com.survive_the_traffic.queue.repository.QueueAdmissionRepository;
import com.survive_the_traffic.queue.service.QueueAdmissionService;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationFacade {

    private final ReservationService reservationService;
    private final QueueAdmissionService queueAdmissionService;

    public ReservationResponse createReservation(ReservationRequest request) {

        boolean admitted = queueAdmissionService.isAdmitted(
            request.concertId(),
            request.userId()
        );

        if(!admitted) {
            throw new AdmissionRequiredException();
        }

        return reservationService.createReservation(request);
    }


}
