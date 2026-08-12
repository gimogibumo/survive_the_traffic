package com.survive_the_traffic.seat.exception;

import com.survive_the_traffic.common.exception.BusinessException;
import com.survive_the_traffic.common.exception.ErrorCode;

public class SeatNotAvailableException extends BusinessException {

    public SeatNotAvailableException() {
        super(ErrorCode.SEAT_NOT_AVAILABLE);
    }
}
