package com.example.vap_back.dto;

import lombok.Data;

@Data
public class BookmarkRequestDto {
    private String url;
    private String title;
    private String press;
    private String time;
}
