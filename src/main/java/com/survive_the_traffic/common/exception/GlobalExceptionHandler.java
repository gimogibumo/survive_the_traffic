package com.survive_the_traffic.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
        BusinessException e
    ) {
        ErrorCode errorCode =
            e.getErrorCode();

        ErrorResponse errorResponse =
            ErrorResponse.from(errorCode);

        return ResponseEntity
            .status(errorCode.getStatus())
            .body(errorResponse);
    }

    @ExceptionHandler(
        MethodArgumentNotValidException.class
    )
    public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException e
    ) {
        ErrorCode errorCode =
            ErrorCode.INVALID_REQUEST;

        return ResponseEntity
            .status(errorCode.getStatus())
            .body(
                ErrorResponse.from(errorCode)
            );
    }

    @ExceptionHandler(
        SeatConcertMismatchException.class
    )
    public ResponseEntity<ErrorResponse> handleSeatConcertMismatch(
        SeatConcertMismatchException e
    ) {
        return ResponseEntity
            .badRequest()
            .body(
                new ErrorResponse(
                    "SEAT_CONCERT_MISMATCH",
                    e.getMessage()
                )
            );
    }
}