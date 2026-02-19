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
        long start = System.currentTimeMillis();

        String method = request.getMethod();
        String uri    = request.getRequestURI();
        String origin = request.getHeader("Origin");

        log.info("[REQUEST ] {} {} | Origin: {}", method, uri, origin);

        filterChain.doFilter(request, response);

        long elapsed = System.currentTimeMillis() - start;
        log.info("[RESPONSE] {} {} | status={} | {}ms", method, uri, response.getStatus(), elapsed);
    }
}
