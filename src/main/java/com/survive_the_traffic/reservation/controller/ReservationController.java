package com.survive_the_traffic.reservation.controller;

import com.survive_the_traffic.auth.security.AuthenticatedUser;
import com.survive_the_traffic.payment.dto.PaymentResponse;
import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import com.survive_the_traffic.reservation.service.ReservationFacade;
import com.survive_the_traffic.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationFacade reservationFacade;

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
        @Valid @RequestBody ReservationRequest request
    ) {
        ReservationResponse response = reservationFacade.createReservation(authenticatedUser.getUserId(),request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reservationId}/payments")
    public ResponseEntity<PaymentResponse> createPayment(@PathVariable Long reservationId) {
        return ResponseEntity.ok(reservationService.createPayment(reservationId));
    }

}
