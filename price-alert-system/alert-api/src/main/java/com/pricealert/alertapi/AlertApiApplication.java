package com.pricealert.alertapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlertApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertApiApplication.class, args);
    }
}
