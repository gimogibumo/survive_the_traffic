package com.survive_the_traffic.queue.dto;

import com.survive_the_traffic.queue.entitiy.QueueStatus;

public record QueueStatusResponse(
    Long concertId,
    Long userId,
    QueueStatus status,
    Long position,
    Long waitingCount
) {

}
