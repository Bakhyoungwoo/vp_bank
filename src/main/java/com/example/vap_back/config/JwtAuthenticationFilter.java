package com.example.vap_back.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        System.out.println("\n[JWT FILTER] REQUEST path = " + path);

        // 토큰 추출
        String token = resolveToken(request);
        if (token == null) {
            System.out.println("[JWT FILTER] Token 없음");
        } else {
            System.out.println("[JWT FILTER] Token 추출됨");
            System.out.println("[JWT FILTER] Token (앞 20자): " +
                    token.substring(0, Math.min(20, token.length())) + "...");
        }

        // 토큰 검증
        if (token != null) {
            boolean valid = jwtTokenProvider.validateToken(token);
            System.out.println("[JWT FILTER] validateToken = " + valid);

            if (valid) {
                Authentication authentication =
                        jwtTokenProvider.getAuthentication(token);

                if (authentication == null) {
                    System.out.println("[JWT FILTER] Authentication 생성 실패");
                } else {
                    System.out.println("[JWT FILTER] Authentication 생성 성공");
                    System.out.println("[JWT FILTER] principal = " + authentication.getPrincipal());
                    System.out.println("[JWT FILTER] authorities = " + authentication.getAuthorities());

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);

                    System.out.println("[JWT FILTER] SecurityContext에 Authentication 저장 완료");
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    // Authorization 헤더에서 Bearer 토큰 추출
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken)) {
            System.out.println("[JWT FILTER] Authorization header = " + bearerToken);

            if (bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
        }
        return null;
    }
}
