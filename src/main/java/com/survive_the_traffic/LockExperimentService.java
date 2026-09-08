package com.survive_the_traffic;

import com.survive_the_traffic.seat.repository.SeatRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class LockExperimentService {

    private final SeatRepository seatRepository;
    private final EntityManager entityManager;


    /**
     * Lock Timeout 실험용.
     *
     * seat 하나의 PESSIMISTIC_WRITE lock을 얻은 뒤
     * holdMillis 동안 transaction을 유지한다.
     */
    @Transactional
    public void holdLock(
        Long seatId,
        CountDownLatch lockAcquiredLatch,
        long holdMillis
    ) {

        System.out.println(
            Thread.currentThread().getName()
                + " : lock 획득 시도"
        );

        seatRepository.findByIdWithPessimisticLock(seatId)
            .orElseThrow();

        /*
         * 여기까지 왔다는 것은
         * SELECT ... FOR UPDATE가 끝났다는 뜻.
         *
         * 즉 이 transaction이 해당 Seat의
         * pessimistic write lock을 획득한 상태다.
         */
        System.out.println(
            Thread.currentThread().getName()
                + " : lock 획득 완료"
        );

        // 테스트 스레드에게
        // "나 진짜 lock 잡았어"라고 알려준다.
        lockAcquiredLatch.countDown();

        sleep(holdMillis);

        System.out.println(
            Thread.currentThread().getName()
                + " : transaction 종료 직전"
        );

        /*
         * 메서드 종료
         * ↓
         * @Transactional commit
         * ↓
         * DB lock release
         */
    }


    /**
     * PostgreSQL lock_timeout을 설정하고
     * 동일한 Seat lock 획득을 시도한다.
     */
    @Transactional
    public void tryLockWithTimeout(
        Long seatId,
        long timeoutMillis
    ) {

        /*
         * SET LOCAL은 현재 transaction에만 적용된다.
         *
         * 예:
         * SET LOCAL lock_timeout = '1000ms'
         */
        entityManager.createNativeQuery(
            "SET LOCAL lock_timeout = '"
                + timeoutMillis
                + "ms'"
        ).executeUpdate();

        System.out.println(
            Thread.currentThread().getName()
                + " : timeout=" + timeoutMillis
                + "ms, lock 획득 시도"
        );

        /*
         * 다른 Transaction이 이미 이 Seat를
         * lock하고 있다면 여기에서 BLOCK된다.
         */
        seatRepository.findByIdWithPessimisticLock(seatId)
            .orElseThrow();

        System.out.println(
            Thread.currentThread().getName()
                + " : lock 획득 성공"
        );
    }


    /**
     * Deadlock 재현용.
     *
     * 첫 번째 Seat를 lock하고,
     * 상대 transaction도 첫 lock을 잡을 때까지 기다렸다가
     * 두 번째 Seat를 lock한다.
     */
    @Transactional
    public void lockTwoSeatsForDeadlock(
        Long firstSeatId,
        Long secondSeatId,
        CyclicBarrier barrier
    ) {

        String thread =
            Thread.currentThread().getName();

        System.out.println(
            thread
                + " : Seat "
                + firstSeatId
                + " lock 시도"
        );

        seatRepository
            .findByIdWithPessimisticLock(firstSeatId)
            .orElseThrow();

        System.out.println(
            thread
                + " : Seat "
                + firstSeatId
                + " lock 획득"
        );

        /*
         * 매우 중요.
         *
         * Tx A와 Tx B 모두 첫 번째 lock을 얻을 때까지
         * 여기서 기다린다.
         *
         * Thread.sleep만 사용하는 것보다
         * deadlock을 훨씬 확실하게 재현할 수 있다.
         */
        awaitBarrier(barrier);

        System.out.println(
            thread
                + " : Seat "
                + secondSeatId
                + " lock 시도"
        );

        /*
         * Tx A:
         *   Seat 1 보유 → Seat 2 요청
         *
         * Tx B:
         *   Seat 2 보유 → Seat 1 요청
         *
         * 여기서 deadlock 발생.
         */
        seatRepository
            .findByIdWithPessimisticLock(secondSeatId)
            .orElseThrow();

        System.out.println(
            thread
                + " : Seat "
                + secondSeatId
                + " lock 획득"
        );
    }


    /**
     * Deadlock 방지 실험.
     *
     * 어떤 순서로 parameter가 들어오든
     * 작은 ID -> 큰 ID 순으로 lock한다.
     */
    @Transactional
    public void lockTwoSeatsInOrder(
        Long seatAId,
        Long seatBId
    ) {

        Long first =
            Math.min(seatAId, seatBId);

        Long second =
            Math.max(seatAId, seatBId);

        String thread =
            Thread.currentThread().getName();

        System.out.println(
            thread
                + " : Seat "
                + first
                + " lock 시도"
        );

        seatRepository
            .findByIdWithPessimisticLock(first)
            .orElseThrow();

        System.out.println(
            thread
                + " : Seat "
                + first
                + " lock 획득"
        );

        /*
         * 실험에서 contention을 눈으로 보기 위한 sleep.
         * 실제 운영 코드에는 넣으면 안 된다.
         */
        sleep(500);

        System.out.println(
            thread
                + " : Seat "
                + second
                + " lock 시도"
        );

        seatRepository
            .findByIdWithPessimisticLock(second)
            .orElseThrow();

        System.out.println(
            thread
                + " : Seat "
                + second
                + " lock 획득"
        );
    }


    private void awaitBarrier(CyclicBarrier barrier) {

        try {

            barrier.await(
                5,
                TimeUnit.SECONDS
            );

        } catch (
            InterruptedException
            | BrokenBarrierException
            | TimeoutException e
        ) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(e);
        }
    }


    private void sleep(long millis) {

        try {

            Thread.sleep(millis);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(e);
        }
    }
}