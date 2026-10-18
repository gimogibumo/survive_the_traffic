package com.survive_the_traffic.common.exception;

public class ReservationAlreadyInProgressException
    extends RuntimeException {

  public ReservationAlreadyInProgressException() {
    super(
        "Reservation is already in progress."
    );
  }
}