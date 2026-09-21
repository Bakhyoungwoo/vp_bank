package com.example.vap_back.config;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceContext {
    public static final String REQUEST_ID = "requestId";

    private TraceContext() {}

    public static String currentOrNew() {
        String current = MDC.get(REQUEST_ID);
        return current == null || current.isBlank() ? UUID.randomUUID().toString() : current;
    }

    public static void set(String requestId) { MDC.put(REQUEST_ID, requestId); }
    public static void clear() { MDC.remove(REQUEST_ID); }
}
