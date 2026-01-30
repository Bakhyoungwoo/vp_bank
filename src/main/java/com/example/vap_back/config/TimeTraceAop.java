package com.example.vap_back.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TimeTraceAop {

    private static final Logger log = LoggerFactory.getLogger(TimeTraceAop.class);

    // 측정 범위: 컨트롤러, 서비스, 카프카 컨슈머, 리포지토리(DB)
    @Around("execution(* com.example.vap_back.controller..*(..)) || " +
            "execution(* com.example.vap_back.service..*(..)) || " +
            "execution(* com.example.vap_back.repository..*(..)) || " +
            "execution(* com.example.vap_back.kafka..*(..))")
    public Object execute(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        try {
            return joinPoint.proceed();
        } finally {
            long finish = System.currentTimeMillis();
            long timeMs = finish - start;

            String methodName = joinPoint.getSignature().toShortString();
            String className = joinPoint.getTarget().getClass().getSimpleName();

            // 1. 임계치 기반 경고 시스템 (300ms 이상 지연 시 경고)
            if (timeMs > 300) {
                log.warn("⚠️  [PERF-ALERT] {} -> {} : {}ms (Critical Delay)", className, methodName, timeMs);
            }
            // 2. 주요 포인트별 커스텀 로그 (수치 증명용)
            else if (className.contains("Redis")) {
                log.info("🚀 [CACHE-HIT] {} : {}ms", methodName, timeMs);
            }
            else if (className.contains("Consumer")) {
                log.info("📩 [KAFKA-PROCESS] {} : {}ms", methodName, timeMs);
            }
            else if (className.contains("Analysis") || methodName.contains("analyze")) {
                log.info("🧠 [AI-ANALYSIS] {} : {}ms", methodName, timeMs);
            }
            else {
                log.info("⏱️  [EXECUTION] {} : {}ms", methodName, timeMs);
            }
        }
    }
}