package com.survive_the_traffic.queue.worker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.survive_the_traffic.queue.repository.ActiveConcertRepository;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import com.survive_the_traffic.queue.service.QueueAdmissionService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueAdmissionWorkerTest {

    @Mock
    ActiveConcertRepository activeConcertRepository;

    @Mock
    WaitingQueueRepository waitingQueueRepository;

    @Mock
    QueueAdmissionService queueAdmissionService;

    @InjectMocks
    QueueAdmissionWorker queueAdmissionWorker;

    @Test
    void waiting이_있는_concert에_global_budget을_나눠서_admission한다() {

        // given
        given(activeConcertRepository.findAll())
            .willReturn(Set.of(1L, 2L));

        given(waitingQueueRepository.getSize(1L))
            .willReturn(100L);

        given(waitingQueueRepository.getSize(2L))
            .willReturn(100L);

        // when
        queueAdmissionWorker.admitWaitingUsers();

        // then
        then(queueAdmissionService)
            .should()
            .admit(1L, 25L);

        then(queueAdmissionService)
            .should()
            .admit(2L, 25L);
    }

    @Test
    void waiting이_없는_concert는_admission_budget에서_제외한다() {

        // given
        given(activeConcertRepository.findAll())
            .willReturn(
                Set.of(1L, 2L)
            );

        given(waitingQueueRepository.getSize(1L))
            .willReturn(100L);

        given(waitingQueueRepository.getSize(2L))
            .willReturn(0L);

        // when
        queueAdmissionWorker.admitWaitingUsers();

        // then
        then(queueAdmissionService)
            .should()
            .admit(1L, 50L);

        then(queueAdmissionService)
            .should(never())
            .admit(eq(2L), anyLong());
    }

    @Test
    void active_concert가_없으면_admission을_실행하지_않는다() {

        // given
        given(activeConcertRepository.findAll())
            .willReturn(Set.of());

        // when
        queueAdmissionWorker.admitWaitingUsers();

        // then
        then(waitingQueueRepository)
            .shouldHaveNoInteractions();

        then(queueAdmissionService)
            .shouldHaveNoInteractions();
    }

}