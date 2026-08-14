package com.survive_the_traffic;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.survive_the_traffic.reservation.dto.ReservationRequest;
import com.survive_the_traffic.reservation.repository.ReservationRepository;
import com.survive_the_traffic.reservation.service.ReservationService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ReservationConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void 동시에_100명이_같은_좌석을_예약한다() throws InterruptedException {

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch donelatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for(int i = 0; i < threadCount; i++) {
            long userId = i + 1; // 각 스레드마다 다른 사용자 ID를 사용
            executor.submit(() -> {
                try {
                    startLatch.await();
                    reservationService.createReservation(new ReservationRequest(userId, 1L)); // 모든 스레드가 같은 좌석 ID를 사용
                    successCount.incrementAndGet();
                } catch(Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    donelatch.countDown();
                }
            });
        }

        startLatch.countDown();
        donelatch.await();

        System.out.println("성공한 예약 수: " + successCount.get());
        System.out.println("실패한 예약 수: " + failureCount.get());

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(99);
    }

}
