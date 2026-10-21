package com.survive_the_traffic;

import com.survive.worker.QueueWorkerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SurviveTheTrafficApplication {

    public static void main(String[] args) {
        String appRole = System.getenv().getOrDefault("APP_ROLE","api");
        if("worker".equalsIgnoreCase(appRole)) {
            SpringApplication application = new SpringApplication(QueueWorkerApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.run(args);
            return;
        }
        SpringApplication.run(SurviveTheTrafficApplication.class, args);
    }

}
