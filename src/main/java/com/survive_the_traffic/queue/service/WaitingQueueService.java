package com.survive_the_traffic.queue.service;

import com.survive_the_traffic.queue.dto.QueueStatusResponse;
import com.survive_the_traffic.queue.repository.QueueStatusRepository;
import com.survive_the_traffic.queue.repository.QueueStatusResult;
import com.survive_the_traffic.queue.repository.WaitingQueueJoinResult;
import com.survive_the_traffic.queue.dto.WaitingQueueResponse;
import com.survive_the_traffic.queue.entity.QueueStatus;
import com.survive_the_traffic.queue.repository.AdmittedQueueRepository;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final AdmittedQueueRepository admittedQueueRepository;
    private final QueueStatusRepository queueStatusRepository;

    public WaitingQueueResponse join(Long concertId, Long userId) {

        WaitingQueueJoinResult result = waitingQueueRepository.join(concertId, userId);



        return new WaitingQueueResponse(
            concertId,userId,result.rank()+1,result.waitingCount()
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

    public QueueStatusResponse getStatus(
        Long concertId,
        Long userId
    ) {

        QueueStatusResult result =
            queueStatusRepository.getStatus(
                concertId,
                userId
            );

        Long position =
            result.rank() == null
                ? null
                : result.rank() + 1;

        return new QueueStatusResponse(
            concertId,
            userId,
            result.status(),
            position,
            result.waitingCount()
        );
    }
}
