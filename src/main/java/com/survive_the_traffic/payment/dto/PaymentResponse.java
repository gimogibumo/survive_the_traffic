package com.survive_the_traffic.payment.dto;

import com.survive_the_traffic.payment.domain.PaymentStatus;

public record PaymentResponse(
    Long paymentId,
    Long reservationId,
    PaymentStatus status,
    Long amount
) {

}
