package com.survive_the_traffic.concert.exception;

import com.survive_the_traffic.common.exception.BusinessException;
import com.survive_the_traffic.common.exception.ErrorCode;
public class ConcertNotFoundException extends BusinessException {

    public ConcertNotFoundException() {
        super(ErrorCode.CONCERT_NOT_FOUND);
    }

}
