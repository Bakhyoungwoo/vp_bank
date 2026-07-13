package com.example.vap_back.controller;

import com.example.vap_back.dto.NewsCreateRequest;
import com.example.vap_back.kafka.NewsCrawlProducer;
import com.example.vap_back.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/news")
@CrossOrigin
@Tag(name = "Internal", description = "?대? ?곕룞 API")
public class InternalNewsController {

    private final NewsService newsService;
    private final NewsCrawlProducer newsCrawlProducer;

    // Python -> Spring?쇰줈 ?댁뒪 ?곗씠??諛쏄린
    @PostMapping
    @Operation(summary = "?댁뒪 ?섏떊", description = "?몃? Python ?쒕쾭?먯꽌 ?댁뒪 ?곗씠?곕? 諛쏆븘 ??ν빀?덈떎.")
    public ResponseEntity<Void> receiveNews(@RequestBody NewsCreateRequest request) {
        log.info("[News Received] ?몃??먯꽌 ?댁뒪 ?섏떊: {}", request.getTitle());
        newsService.saveNews(request);
        return ResponseEntity.ok().build();
    }

    // Spring -> Kafka ?щ·???몄텧
    @PostMapping("/crawl")
    @Operation(summary = "?щ·留??쒖옉", description = "吏?뺥븳 移댄뀒怨좊━?????Kafka ?щ·留??쒖옉 硫붿떆吏瑜?諛쏆븥遺덈떎.")
    public ResponseEntity<String> triggerCrawl(@RequestParam("category") String category) {
        String normalizedCategory = category.trim().toLowerCase();
        log.info("[Command] ?щ·留??쒖옉 硫붿떆吏 ?꾩넚: category={}", normalizedCategory);

        newsCrawlProducer.requestCrawl(normalizedCategory);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("crawl request accepted");
    }
}
