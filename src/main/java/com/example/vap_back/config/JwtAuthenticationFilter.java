package com.example.vap_back.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j // 로거
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth")
                || path.startsWith("/api/internal");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        log.info("[JWT FILTER] REQUEST path = {}", path);

        // 토큰 추출
        String token = resolveToken(request);
        if (token == null) {
            log.warn("[JWT FILTER] Token 없음");
        } else {
            log.debug("[JWT FILTER] Token 추출됨");
            // 보안상 전체 토큰 로깅은 지양하므로 DEBUG 레벨에서만 확인
            if (log.isDebugEnabled()) {
                log.debug("[JWT FILTER] Token (앞 20자): {}...",
                        token.substring(0, Math.min(20, token.length())));
            }
        }

        // 토큰 검증
        if (token != null) {
            boolean valid = jwtTokenProvider.validateToken(token);
            log.debug("[JWT FILTER] validateToken = {}", valid);

            if (valid) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token);

                if (authentication == null) {
                    log.error("[JWT FILTER] Authentication 생성 실패");
                } else {
                    log.debug("[JWT FILTER] Authentication 생성 성공");
                    log.debug("[JWT FILTER] principal = {}", authentication.getPrincipal());
                    log.debug("[JWT FILTER] authorities = {}", authentication.getAuthorities());

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("[JWT FILTER] SecurityContext에 Authentication 저장 완료");
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    // Authorization 헤더에서 Bearer 토큰 추출
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken)) {
            log.trace("[JWT FILTER] Authorization header = {}", bearerToken); // 민감 정보일 수 있으므로 trace

            if (bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
        }
        return null;
    }
}