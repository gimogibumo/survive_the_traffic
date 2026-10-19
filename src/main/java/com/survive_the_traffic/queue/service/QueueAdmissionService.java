package com.survive_the_traffic.queue.service;

import com.survive_the_traffic.queue.repository.AdmittedQueueRepository;
import com.survive_the_traffic.queue.repository.QueueAdmissionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QueueAdmissionService {

    private final QueueAdmissionRepository
        queueAdmissionRepository;

    private final AdmittedQueueRepository
        admittedQueueRepository;


    public QueueAdmissionService(
        QueueAdmissionRepository queueAdmissionRepository,
        AdmittedQueueRepository admittedQueueRepository
    ) {

        this.queueAdmissionRepository =
            queueAdmissionRepository;

        this.admittedQueueRepository =
            admittedQueueRepository;
    }


    /**
     * Waiting Queue에서 count명까지
     * ADMITTED 상태로 이동시킨다.
     *
     * 실제 WAITING -> ADMITTED 이동은
     * Redis Lua script에서 atomic하게 처리된다.
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
     * 사용자가 현재 해당 공연의
     * 유효한 admission을 가지고 있는지 확인한다.
     *
     * ReservationFacade에서 reservation API의
     * admission gate로 사용한다.
     *
     * admission은 일회성 reservation token이 아니라
     * 만료 시간까지 유효한 access window이므로,
     * 이 메서드는 상태를 소비하거나 변경하지 않는다.
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
}