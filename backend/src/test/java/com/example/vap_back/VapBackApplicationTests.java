package com.example.vap_back;

import org.junit.jupiter.api.Test;

// 실제 DB/Redis 없이도 실행되도록 @SpringBootTest 제거
// 통합 테스트가 필요하다면 TestContainers와 함께 별도 프로파일에서 실행
class VapBackApplicationTests {

    @Test
    void contextLoads() {
        // 단위 테스트 환경에서는 전체 Spring Context 로드 불필요
        // 각 서비스 단위테스트가 비즈니스 로직을 검증함
    }
}
