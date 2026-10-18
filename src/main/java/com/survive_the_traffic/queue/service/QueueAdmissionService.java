package com.survive_the_traffic.queue.service;

import com.survive_the_traffic.queue.dto.AdmissionClaimResult;
import com.survive_the_traffic.queue.entity.AdmissionRestoreStatus;
import com.survive_the_traffic.queue.repository.AdmissionClaimRepository;
import com.survive_the_traffic.queue.repository.AdmittedQueueRepository;
import com.survive_the_traffic.queue.repository.QueueAdmissionRepository;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
public class QueueAdmissionService {

    private final QueueAdmissionRepository
        queueAdmissionRepository;

    private final AdmittedQueueRepository
        admittedQueueRepository;

    private final AdmissionClaimRepository
        admissionClaimRepository;


    public QueueAdmissionService(
        QueueAdmissionRepository queueAdmissionRepository,
        AdmittedQueueRepository admittedQueueRepository,
        AdmissionClaimRepository admissionClaimRepository
    ) {

        this.queueAdmissionRepository =
            queueAdmissionRepository;

        this.admittedQueueRepository =
            admittedQueueRepository;

        this.admissionClaimRepository =
            admissionClaimRepository;
    }


    /**
     * Waiting Queue에서 count명까지
     * ADMITTED 상태로 이동시킨다.
     *
     * 실제 이동은 Redis Lua script에서 atomic하게 처리된다.
     */
    public List<Long> admit(
        Long concertId,
        long count
    ) {

        return queueAdmissionRepository
            .admit(
                concertId,
                count
            );
    }


    /**
     * 단순 상태 조회 용도.
     *
     * Reservation gate에서는 사용하지 않는다.
     * Reservation gate는 반드시 claim()을 사용해야 한다.
     */
    public boolean isAdmitted(
        Long concertId,
        Long userId
    ) {

        return admittedQueueRepository
            .isAdmitted(
                concertId,
                userId
            );
    }


    /**
     * Reservation 요청이 admission을 사용할 권리를
     * 원자적으로 점유한다.
     *
     * ADMITTED -> RESERVING
     */
    public AdmissionClaimResult claim(
        Long concertId,
        Long userId
    ) {

        return admissionClaimRepository
            .claim(
                concertId,
                userId
            );
    }


    /**
     * Reservation 성공 후 admission을 소비한다.
     *
     * RESERVING -> CONSUMED
     */
    public boolean complete(
        Long concertId,
        Long userId
    ) {

        return admissionClaimRepository
            .complete(
                concertId,
                userId
            );
    }


    /**
     * Reservation 실패 시 admission을 복구한다.
     *
     * RESERVING -> ADMITTED
     */
    public AdmissionRestoreStatus restore(
        Long concertId,
        Long userId
    ) {

        return admissionClaimRepository
            .restore(
                concertId,
                userId
            );
    }
}
