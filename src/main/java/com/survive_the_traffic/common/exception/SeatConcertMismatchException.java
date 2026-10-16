package com.survive_the_traffic.common.exception;

public class SeatConcertMismatchException extends RuntimeException {

    public SeatConcertMismatchException() {
        super("Seat does not belong to the requested concert");
    }
}
