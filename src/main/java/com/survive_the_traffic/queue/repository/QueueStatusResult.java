package com.survive_the_traffic.queue.repository;

import com.survive_the_traffic.queue.entity.QueueStatus;

public record QueueStatusResult(
    QueueStatus status,
    Long rank,
    long waitingCount
) {
}