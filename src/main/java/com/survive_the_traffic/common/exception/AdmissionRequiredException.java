package com.survive_the_traffic.common.exception;

public class AdmissionRequiredException extends BusinessException{

    public AdmissionRequiredException() {
        super(ErrorCode.ADMISSION_REQUIRED);
    }

}
