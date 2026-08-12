package com.survive_the_traffic.seat.exception;

import com.survive_the_traffic.common.exception.BusinessException;
import com.survive_the_traffic.common.exception.ErrorCode;

public class SeatNotFoundException extends BusinessException {

    public SeatNotFoundException() {
        super(ErrorCode.SEAT_NOT_FOUND);
    }

}
