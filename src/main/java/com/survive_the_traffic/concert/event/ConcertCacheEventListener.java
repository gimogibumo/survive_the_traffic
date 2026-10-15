package com.survive_the_traffic.concert.event;

import com.survive_the_traffic.concert.cache.ConcertCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ConcertCacheEventListener {

    private final ConcertCacheRepository concertCacheRepository;

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleConcertUpdated(ConcertUpdatedEvent event) {

        System.out.println("3. AFTER COMMIT - CACHE DELETE");

        concertCacheRepository.delete(event.concertId());
    }

}
