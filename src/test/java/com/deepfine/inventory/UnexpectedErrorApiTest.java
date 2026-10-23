package com.deepfine.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예상하지 못한 예외도 다른 에러와 같은 ProblemDetail + code 형식으로 나가야 한다.
 * 내부 예외 메시지는 클라이언트에 새지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import({TestcontainersConfiguration.class, UnexpectedErrorApiTest.ThrowingController.class})
class UnexpectedErrorApiTest {

    /** 테스트에서만 등록되는 컨트롤러. 처리되지 않은 예외를 일부러 던진다. */
    @RestController
    static class ThrowingController {

        @GetMapping("/test/unexpected")
        String unexpected() {
            throw new IllegalStateException("내부 사정: 새면 안 되는 메시지");
        }
    }

    @Autowired
    RestTestClient client;

    @Test
    void 예상하지_못한_예외는_500_ProblemDetail과_INTERNAL_ERROR_코드로_나간다() {
        client.get().uri("/test/unexpected")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.detail").value(detail -> org.assertj.core.api.Assertions.assertThat((String) detail)
                        .doesNotContain("내부 사정"));
    }
}
