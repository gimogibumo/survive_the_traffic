package com.survive_the_traffic.queue.dto;

import com.survive_the_traffic.queue.entity.WaitingQueueJoinStatus;

public record WaitingQueueJoinResult(
    WaitingQueueJoinStatus status,
    long rank,
    long waitingCount
) {

    public boolean added() {
        return status == WaitingQueueJoinStatus.JOINED;
    }

    public WaitingQueueJoinResult(
        boolean added,
        long rank,
        long waitingCount
    ) {
        this(
            added
                ? WaitingQueueJoinStatus.JOINED
                : WaitingQueueJoinStatus.ALREADY_WAITING,
            rank,
            waitingCount
        );
    }
}