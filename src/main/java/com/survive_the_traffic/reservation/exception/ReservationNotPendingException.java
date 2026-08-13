package com.survive_the_traffic.reservation.exception;

import com.survive_the_traffic.common.exception.BusinessException;
import com.survive_the_traffic.common.exception.ErrorCode;

public class ReservationNotPendingException extends BusinessException {

    public ReservationNotPendingException() {
        super(ErrorCode.RESERVATION_NOT_PENDING);
    }
}
