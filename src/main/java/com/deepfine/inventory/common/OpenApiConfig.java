package com.deepfine.inventory.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 문서의 제목과 설명. 각 API의 설명은 컨트롤러의 어노테이션에 있다. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("재고 관리 시스템 MVP")
                .version("0.0.1")
                .description("""
                        상품의 입고, 출고, 재고 조회 API. 동시 요청이 몰려도 재고가 음수가 되거나 초과 출고가 생기지 않는다.

                        에러 응답은 RFC 9457 ProblemDetail 형식에 `code`를 더한다: \
                        `INVALID_REQUEST`(400), `PRODUCT_NOT_FOUND`(404), `INSUFFICIENT_STOCK`(409, `requested`와 `available` 포함), \
                        `INTERNAL_ERROR`(500, 예상하지 못한 오류)."""));
    }
}
