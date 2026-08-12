package com.survive_the_traffic.common.exception;

public record ErrorResponse (
    String code,
    String message
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }
}