package com.survive_the_traffic.reservation.exception;

import com.survive_the_traffic.common.exception.BusinessException;
import com.survive_the_traffic.common.exception.ErrorCode;

public class ReservationExpiredException extends BusinessException {

    public ReservationExpiredException() {
        super(ErrorCode.RESERVATION_EXPIRED);
    }
}
