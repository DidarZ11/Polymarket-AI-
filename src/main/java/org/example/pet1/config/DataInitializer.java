package org.example.pet1.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pet1.service.MarketService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Запускает синхронизацию рынков сразу после старта приложения.
 * ApplicationReadyEvent гарантирует, что БД и все бины уже готовы.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final MarketService marketService;

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        log.info("Приложение запущено — выполняю начальную синхронизацию рынков...");
        try {
            int count = marketService.syncMarkets();
            log.info("Начальная синхронизация завершена: {} рынков загружено", count);
        } catch (Exception e) {
            log.error("Ошибка начальной синхронизации рынков: {}", e.getMessage());
        }
    }
}
