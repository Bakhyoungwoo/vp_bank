package com.example.vap_back.dto;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NewsCreateRequest {

    private String category;
    private String title;
    private String content;
    private String url;
    private String press;
    private LocalDateTime publishedAt;
}
