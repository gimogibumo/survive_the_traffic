package com.survive_the_traffic.queue.worker;

import com.survive_the_traffic.queue.repository.ActiveConcertRepository;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import com.survive_the_traffic.queue.service.QueueAdmissionService;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "queue.admission-worder.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class QueueAdmissionWorker {

    private static final long GLOBAL_BATCH_SIZE = 50L;

    private final ActiveConcertRepository activeConcertRepository;
    private final WaitingQueueRepository waitingQueueRepository;
    private final QueueAdmissionService queueAdmissionService;

    @Scheduled(fixedRate = 100L)
    public void admitWaitingUsers() {

        Set<Long> activeConcerts =
            activeConcertRepository.findAll();

        if(activeConcerts.isEmpty()) {
            return;
        }

        List<Long> nonEmptyConcerts =
            activeConcerts.stream()
                .filter(concertId ->
                    waitingQueueRepository.getSize(concertId) > 0)
                .sorted(Comparator.naturalOrder())
                .toList();

        if(nonEmptyConcerts.isEmpty()) {return;}

        long base = GLOBAL_BATCH_SIZE / nonEmptyConcerts.size();
        long remainder = GLOBAL_BATCH_SIZE % nonEmptyConcerts.size();

        for(int i = 0; i < nonEmptyConcerts.size(); i++) {

            long batchSize = base + (i < remainder ? 1 : 0);

            if(batchSize == 0) continue;

            queueAdmissionService.admit(
                nonEmptyConcerts.get(i), batchSize
            );
        }
    }


}
