package org.example.pet1.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pet1.dto.MarketAnalysisDto;
import org.example.pet1.service.AnalysisHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Контроллер для работы с историей AI-анализов.
 */
@Slf4j
@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisHistoryService analysisHistoryService;

    /**
     * GET /api/analyses/recent
     * Последние 10 анализов по всем рынкам — для главной страницы дашборда.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<MarketAnalysisDto>> getRecentAnalyses() {
        log.debug("GET /api/analyses/recent");
        return ResponseEntity.ok(analysisHistoryService.getRecentAnalyses());
    }
}
