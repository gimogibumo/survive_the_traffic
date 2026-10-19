package com.survive_the_traffic.auth.exception;

import com.survive_the_traffic.common.exception.BusinessException;
import com.survive_the_traffic.common.exception.ErrorCode;

public class DuplicateEmailException extends BusinessException {

    public DuplicateEmailException() {
        super(ErrorCode.DUPLICATE_EMAIL);
    }
}
