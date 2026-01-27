package com.example.vap_back.dto;

import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
@Getter
public class NewsCreateRequest {

    private String category;
    private String title;
    private String content;
    private String url;
    private String press;
    @JsonProperty("published_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;
    private List<String> keywords;
}
