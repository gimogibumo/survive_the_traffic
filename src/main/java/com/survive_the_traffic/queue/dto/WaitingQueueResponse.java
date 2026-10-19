package com.survive_the_traffic.queue.dto;

public record WaitingQueueResponse(
    Long concertId,
    Long userId,
    Long position,
    Long waitingCount
) {

}
