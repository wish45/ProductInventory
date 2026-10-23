package com.deepfine.inventory;

import org.junit.jupiter.api.Test;

/** 운영 가시성: 헬스 체크가 DB 연결까지 포함해 UP 을 돌려준다. */
class HealthApiTest extends ApiTestSupport {

    @Test
    void 헬스_체크가_UP이다() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
