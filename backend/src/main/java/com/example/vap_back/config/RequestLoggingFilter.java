package com.example.vap_back.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // Spring Security(-100)보다 먼저 실행되어야 모든 요청 로깅 가능
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.nanoTime();
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) requestId = TraceContext.currentOrNew();
        TraceContext.set(requestId);
        response.setHeader("X-Request-Id", requestId);

        String method = request.getMethod();
        String uri    = request.getRequestURI();
        String origin = request.getHeader("Origin");

        log.info("[REQUEST ] method={} uri={} requestId={} origin={}", method, uri, requestId, origin);
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("[RESPONSE] method={} uri={} requestId={} status={} elapsedMs={}",
                    method, uri, requestId, response.getStatus(),
                    (System.nanoTime() - start) / 1_000_000);
            TraceContext.clear();
        }
    }
}
