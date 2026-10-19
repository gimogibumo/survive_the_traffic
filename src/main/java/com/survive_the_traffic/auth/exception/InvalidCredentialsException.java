package com.survive_the_traffic.auth.exception;

import com.survive_the_traffic.common.exception.BusinessException;
import com.survive_the_traffic.common.exception.ErrorCode;

public class InvalidCredentialsException
    extends BusinessException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS);
    }
}