package com.example.vap_back.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ThreadLocalRandom;

// 블루그린 배포 자동 롤백(5xx 비율 감시) 검증 전용. fault.inject.rate(=FAULT_INJECT_RATE)
// 프로퍼티가 없으면 이 설정 자체가 로드되지 않으므로 운영 동작에는 영향이 없다.
@Slf4j
@Configuration
@ConditionalOnProperty(name = "fault.inject.rate")
public class FaultInjectionConfig implements WebMvcConfigurer {

    @Value("${fault.inject.rate}")
    private double rate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.warn("Fault injection ENABLED: rate={}", rate);
        registry.addInterceptor(new FaultInjectionInterceptor(rate))
                .excludePathPatterns("/actuator/**");
    }

    private static class FaultInjectionInterceptor implements HandlerInterceptor {
        private final double rate;

        FaultInjectionInterceptor(double rate) {
            this.rate = rate;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (ThreadLocalRandom.current().nextDouble() < rate) {
                throw new FaultInjectedException();
            }
            return true;
        }
    }

    static class FaultInjectedException extends RuntimeException {
        FaultInjectedException() {
            super("fault injection triggered");
        }
    }
}
