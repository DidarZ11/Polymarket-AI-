package org.example.pet1.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pet1.service.MarketService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final MarketService marketService;

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        log.info("DataInitializer запущен");
        log.info("Запускаю синхронизацию рынков при старте (без условий)...");
        try {
            int count = marketService.syncMarkets();
            log.info("Начальная синхронизация завершена: {} рынков загружено", count);
        } catch (Exception e) {
            log.error("Ошибка начальной синхронизации рынков: {}", e.getMessage(), e);
        }
    }
}
