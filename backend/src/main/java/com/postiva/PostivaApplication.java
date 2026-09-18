package com.postiva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PostivaApplication {
    public static void main(String[] args) {
        SpringApplication.run(PostivaApplication.class, args);
    }
}
