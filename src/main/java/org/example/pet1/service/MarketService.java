package org.example.pet1.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pet1.client.PolymarketClient;
import org.example.pet1.dto.MarketDto;
import org.example.pet1.entity.Market;
import org.example.pet1.repository.MarketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Сервисный слой: бизнес-логика работы с рынками.
 * Связывает HTTP-клиент Polymarket с базой данных.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    private final MarketRepository marketRepository;
    private final PolymarketClient polymarketClient;

    /** Возвращает все рынки из нашей базы данных */
    public List<Market> getAllMarkets() {
        return marketRepository.findAll();
    }

    /** Возвращает рынок по id из базы данных */
    public Optional<Market> getMarketById(Long id) {
        return marketRepository.findById(id);
    }

    /** Возвращает только активные рынки */
    public List<Market> getActiveMarkets() {
        return marketRepository.findByActive(true);
    }

    /**
     * Загружает свежие данные с Polymarket Gamma API и сохраняет рынки в БД.
     * Уже существующие рынки (по marketId) обновляются, новые — создаются.
     *
     * @return количество сохранённых рынков
     */
    @Transactional
    public int syncMarkets() {
        log.info("Синхронизация рынков с Polymarket Gamma API...");

        List<MarketDto> dtos = polymarketClient.fetchMarkets();
        int count = 0;

        for (MarketDto dto : dtos) {
            if (dto.getId() == null) continue;

            // Обновляем существующий или создаём новый рынок
            Market market = marketRepository.findByMarketId(dto.getId())
                    .orElse(new Market());

            market.setMarketId(dto.getId());
            market.setQuestion(dto.getQuestion());
            market.setOutcomePrices(dto.getOutcomePrices());
            market.setVolume(dto.getVolume());
            market.setEndDate(dto.getEndDate());
            market.setActive(dto.getActive());

            marketRepository.save(market);
            count++;
        }

        log.info("Сохранено рынков: {}", count);
        return count;
    }
}
