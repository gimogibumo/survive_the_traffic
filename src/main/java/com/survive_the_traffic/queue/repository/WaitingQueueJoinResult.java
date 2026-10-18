package com.survive_the_traffic.queue.repository;

public record WaitingQueueJoinResult(
    boolean added,
    long rank,
    long waitingCount
) {

}
