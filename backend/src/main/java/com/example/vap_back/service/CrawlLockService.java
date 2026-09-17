package com.example.vap_back.service;

public interface CrawlLockService {
    boolean isLocked(String category);
    void lock(String category);
    void unlock(String category);
}
