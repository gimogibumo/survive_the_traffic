package com.survive_the_traffic.common.exception;

public class AdmissionRequiredException extends RuntimeException{

    public AdmissionRequiredException() {
        super("Reservation admission is required");
    }

}
