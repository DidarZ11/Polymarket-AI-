package org.example.pet1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class Pet1Application {

    public static void main(String[] args) {
        SpringApplication.run(Pet1Application.class, args);
    }
}
