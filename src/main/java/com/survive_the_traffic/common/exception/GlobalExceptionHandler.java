package com.survive_the_traffic.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse errorResponse = ErrorResponse.from(errorCode);

        return ResponseEntity
            .status(errorCode.getStatus())
            .body(errorResponse);
    }

    @ExceptionHandler(AdmissionRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAdmissionRequired(
        AdmissionRequiredException e
    ) {
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(
                new ErrorResponse(
                    "ADMISSION_REQUIRED",
                    e.getMessage()
                )
            );
    }
}
