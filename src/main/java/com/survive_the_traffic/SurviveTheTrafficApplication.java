package com.survive_the_traffic;

import com.survive.migration.MigrationApplication;
import com.survive.worker.QueueWorkerApplication;
import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SurviveTheTrafficApplication {

    public static void main(String[] args) {
        String appRole = System.getenv().getOrDefault("APP_ROLE","api").
            trim().toLowerCase(Locale.ROOT);

        switch (appRole) {

            case "api" -> runApi(args);

            case "worker" -> runWorker(args);

            case "migration" -> runMigration(args);

            default -> throw new IllegalArgumentException("Invalid app role");
        }
    }

    private static void runApi(String[] args) {
        SpringApplication.run(SurviveTheTrafficApplication.class, args);
    }

    private static void runWorker(String[] args) {
        SpringApplication application = new SpringApplication(QueueWorkerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }

    private static void runMigration(String[] args) {
        SpringApplication application = new SpringApplication(MigrationApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = application.run(args);
        int exitCode = SpringApplication.exit(context);
        System.exit(exitCode);
    }

}
