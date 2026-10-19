package com.survive_the_traffic.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "잘못된 요청입니다."
    ),

    DUPLICATE_EMAIL(
        HttpStatus.CONFLICT,
        "DUPLICATE_EMAIL",
        "이미 사용 중인 이메일입니다."
    ),

    USER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "USER_NOT_FOUND",
        "유저를 찾을 수 없습니다."
    ),

    CONCERT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "CONCERT_NOT_FOUND",
        "콘서트를 찾을 수 없습니다."
    ),
    SEAT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SEAT_NOT_FOUND",
        "좌석을 찾을 수 없습니다."
    ),
    SEAT_NOT_AVAILABLE(
        HttpStatus.CONFLICT,
        "SEAT_NOT_AVAILABLE",
        "이미 선택되었거나 예약된 좌석입니다."
    ),
    INTERNAL_SERVER_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_SERVER_ERROR",
        "서버 내부 오류가 발생했습니다."
    ),
    RESERVATION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "RESERVATION_NOT_FOUND",
        "예약을 찾지 못했습니다."
    ),
    RESERVATION_NOT_PENDING(
        HttpStatus.CONFLICT,
        "RESERVATION_NOT_PENDING",
        "예약 상태가 'PENDING'이 아닙니다."
    ),
    RESERVATION_EXPIRED(
        HttpStatus.CONFLICT,
        "RESERVATION_EXPIRED",
        "예약이 만료되었습니다."
    );
    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

}
