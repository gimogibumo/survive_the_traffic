package com.survive_the_traffic.queue.service;

import com.survive_the_traffic.queue.dto.WaitingQueueResponse;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;

    public WaitingQueueResponse join(Long concertId, Long userId) {

        waitingQueueRepository.join(concertId, userId);

        Long rank = waitingQueueRepository.getRank(concertId, userId);

        if(rank == null) {
            throw new IllegalStateException("Waiting Queue join failed");
        }

        Long waitingCount = waitingQueueRepository.getSize(concertId);

        return new WaitingQueueResponse(
            concertId,userId,rank+1,waitingCount
        );
    }

    public WaitingQueueResponse getPosition(Long concertId, Long userId) {

        Long rank = waitingQueueRepository.getRank(concertId, userId);

        if(rank == null) {
            throw new IllegalStateException("User in not waiting queue");
        }

        Long waitingCount = waitingQueueRepository.getSize(concertId);

        return new WaitingQueueResponse(
            concertId,userId,rank+1,waitingCount
        );
    }
}
