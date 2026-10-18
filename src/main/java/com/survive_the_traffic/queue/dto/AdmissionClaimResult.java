package com.survive_the_traffic.queue.dto;

import com.survive_the_traffic.queue.entity.AdmissionClaimStatus;

public record AdmissionClaimResult(
    AdmissionClaimStatus status,
    Long expiresAt
) {

    public boolean claimed() {

        return status
            == AdmissionClaimStatus.CLAIMED;
    }
}
