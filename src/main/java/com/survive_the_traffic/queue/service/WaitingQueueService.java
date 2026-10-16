package com.survive_the_traffic.queue.service;

import com.survive_the_traffic.queue.dto.QueueStatusResponse;
import com.survive_the_traffic.queue.dto.WaitingQueueResponse;
import com.survive_the_traffic.queue.entitiy.QueueStatus;
import com.survive_the_traffic.queue.repository.ActiveConcertRepository;
import com.survive_the_traffic.queue.repository.AdmittedQueueRepository;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final AdmittedQueueRepository admittedQueueRepository;

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

    public QueueStatusResponse getStatus(Long concertId, Long userId) {

        Long waitingCount = waitingQueueRepository.getSize(concertId);

        if(admittedQueueRepository.isAdmitted(concertId,userId)) {
            return new QueueStatusResponse(
                concertId,
                userId,
                QueueStatus.ADMITTED,
                null,
                waitingCount
            );
        }

        Long rank = waitingQueueRepository.getRank(concertId, userId);

        if(rank != null) {
            return new QueueStatusResponse(
                concertId,
                userId,
                QueueStatus.WAITING,
                rank+1,
                waitingCount
            );
        }

        return new QueueStatusResponse(
            concertId,
            userId,
            QueueStatus.NOT_JOINED,
            null,
            waitingCount
        );
    }
}
