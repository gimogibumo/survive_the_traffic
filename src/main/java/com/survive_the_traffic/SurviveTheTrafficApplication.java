package com.survive_the_traffic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SurviveTheTrafficApplication {

    public static void main(String[] args) {
        SpringApplication.run(SurviveTheTrafficApplication.class, args);
    }

}
