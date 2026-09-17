package com.example.vap_back.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class TimeTraceAop {

    // 현재 프로젝트의 패키지 구조에 맞춰 범위를 설정합니다.
    @Around("execution(* com.example.vap_back.controller..*(..)) || execution(* com.example.vap_back.service..*(..))")
    public Object execute(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        try {
            return joinPoint.proceed();
        } finally {
            long finish = System.currentTimeMillis();
            long timeMs = finish - start;

            // 100ms를 기준으로 지연 발생 시 WARN 로그를 남겨 모니터링을 강화합니다.
            if (timeMs > 100) {
                log.warn("⏱️ [SLOW] {} : {}ms", joinPoint.getSignature().toShortString(), timeMs);
            } else {
                log.info("⏱️ [EXECUTION] {} : {}ms", joinPoint.getSignature().toShortString(), timeMs);
            }
        }
    }
}