package com.survive_the_traffic.queue.service;

import com.survive_the_traffic.queue.repository.AdmittedQueueRepository;
import com.survive_the_traffic.queue.repository.QueueAdmissionRepository;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueAdmissionService {

    private final QueueAdmissionRepository queueAdmissionRepository;
    private final AdmittedQueueRepository admittedQueueRepository;

    public List<Long> admit(Long concertId, Long counts) {
        return queueAdmissionRepository.admit(concertId,counts);
    }

    public boolean isAdmitted(Long concertId, Long userId) {
        return admittedQueueRepository.isAdmitted(concertId,userId);
    }


}
