package com.survive_the_traffic.queue.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.survive_the_traffic.queue.repository.ActiveConcertRepository;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import com.survive_the_traffic.queue.service.QueueAdmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class QueueAdmissionWorkerConditionalTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                TestConfiguration.class
            )
            .withBean(
                ActiveConcertRepository.class,
                () -> mock(
                    ActiveConcertRepository.class
                )
            )
            .withBean(
                WaitingQueueRepository.class,
                () -> mock(
                    WaitingQueueRepository.class
                )
            )
            .withBean(
                QueueAdmissionService.class,
                () -> mock(
                    QueueAdmissionService.class
                )
            );


    @Test
    void 설정이_없으면_AdmissionWorker가_생성되지_않는다() {

        contextRunner.run(
            context -> {

                assertThat(
                    context
                ).doesNotHaveBean(
                    QueueAdmissionWorker.class
                );
            }
        );
    }


    @Test
    void AdmissionWorker가_false이면_생성되지_않는다() {

        contextRunner
            .withPropertyValues(
                "queue.admission-worker.enabled=false"
            )
            .run(
                context -> {

                    assertThat(
                        context
                    ).doesNotHaveBean(
                        QueueAdmissionWorker.class
                    );
                }
            );
    }


    @Test
    void AdmissionWorker가_true이면_생성된다() {

        contextRunner
            .withPropertyValues(
                "queue.admission-worker.enabled=true"
            )
            .run(
                context -> {

                    assertThat(
                        context
                    ).hasSingleBean(
                        QueueAdmissionWorker.class
                    );
                }
            );
    }


    @Configuration(proxyBeanMethods = false)
    @Import(QueueAdmissionWorker.class)
    static class TestConfiguration {
    }
}