package org.example.techstarsscraper.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.techstarsscraper.model.Job;
import org.example.techstarsscraper.service.GoogleSheetsService;
import org.example.techstarsscraper.service.ScraperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class ScrapeController {
    private final ScraperService scraperService;

    public ScrapeController(ScraperService scraperService ) {
        this.scraperService = scraperService;
    }

    @PostMapping("/scrape")
    public ResponseEntity<?> scrape(@RequestParam("function") String jobFunction) {
        try {
            List<Job> result = scraperService.scrapeByFunction(jobFunction);
            return ResponseEntity.ok(Map.of("saved", result.size()));
        } catch (Exception e) {
            log.error("Error during scraping or saving to Sheets", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


    @GetMapping("/jobs")
    public ResponseEntity<List<Job>> getAll() {
        return ResponseEntity.ok(scraperService.findAll());
    }
}