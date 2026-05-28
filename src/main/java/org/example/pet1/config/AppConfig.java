package org.example.pet1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурация Spring-бинов приложения.
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate — синхронный HTTP-клиент для запросов к внешним API (в т.ч. Polymarket).
     * Объявлен как бин, чтобы Spring мог его внедрять через @RequiredArgsConstructor.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
