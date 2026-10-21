package com.survive.worker;
import com.survive_the_traffic.common.config.RedisScriptConfig;
import com.survive_the_traffic.common.config.TimeConfig;
import com.survive_the_traffic.queue.repository.ActiveConcertRepository;
import com.survive_the_traffic.queue.repository.AdmittedQueueRepository;
import com.survive_the_traffic.queue.repository.QueueAdmissionRepository;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import com.survive_the_traffic.queue.service.QueueAdmissionService;
import com.survive_the_traffic.queue.worker.QueueAdmissionWorker;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAutoConfiguration(
    exclude = {
        DataSourceAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
    }
)
@EnableScheduling
@Import({
    TimeConfig.class,
    RedisScriptConfig.class,

    ActiveConcertRepository.class,
    WaitingQueueRepository.class,
    QueueAdmissionRepository.class,
    AdmittedQueueRepository.class,

    QueueAdmissionService.class,
    QueueAdmissionWorker.class
})
public class QueueWorkerApplication {



}
