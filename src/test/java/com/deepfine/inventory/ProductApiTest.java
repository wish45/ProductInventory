package com.deepfine.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProductApiTest extends ApiTestSupport {

    @Nested
    class 입고 {

        @Test
        void 미등록_상품이면_신규_등록_후_입고한다() {
            String code = newCode();

            ProductJson product = inbound(code, "상품 A", 10);

            assertThat(product.id()).isPositive();
            assertThat(product.code()).isEqualTo(code);
            assertThat(product.name()).isEqualTo("상품 A");
            assertThat(product.quantity()).isEqualTo(10);
        }

        @Test
        void 등록된_상품이면_수량만_더하고_이름은_기존_것을_유지한다() {
            String code = newCode();
            ProductJson first = inbound(code, "상품 A", 10);

            ProductJson second = inbound(code, "다른 이름", 5);

            assertThat(second.id()).isEqualTo(first.id());
            assertThat(second.name()).isEqualTo("상품 A");
            assertThat(second.quantity()).isEqualTo(15);
        }

        @Test
        void 수량이_상한을_넘으면_400이고_어떤_필드가_틀렸는지_알려준다() {
            postInbound(newCode(), "상품 A", 1_000_001)
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                    .jsonPath("$.detail").isEqualTo("요청 값이 올바르지 않습니다.")
                    .jsonPath("$.errors[0].field").isEqualTo("quantity");
        }

        @Test
        void 상품_코드가_비어_있으면_400이고_어떤_필드가_틀렸는지_알려준다() {
            postInbound(" ", "상품 A", 1)
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                    .jsonPath("$.errors[0].field").isEqualTo("code");
        }
    }

    @Nested
    class 조회 {

        @Test
        void 상품의_현재_재고를_돌려준다() {
            ProductJson created = inbound(newCode(), "상품 B", 7);

            ProductJson found = getProduct(created.id());

            assertThat(found).isEqualTo(created);
        }

        @Test
        void 없는_상품이면_404이다() {
            client.get().uri("/api/products/{id}", Long.MAX_VALUE)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody().jsonPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
        }
    }

    @Nested
    class 출고 {

        @Test
        void 재고를_줄이고_남은_수량을_돌려준다() {
            ProductJson created = inbound(newCode(), "상품 C", 10);

            ProductJson after = postOutbound(created.id(), 3)
                    .expectStatus().isOk()
                    .expectBody(ProductJson.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(after.quantity()).isEqualTo(7);
            assertThat(getProduct(created.id()).quantity()).isEqualTo(7);
        }

        @Test
        void 재고가_부족하면_409이고_재고는_그대로다() {
            ProductJson created = inbound(newCode(), "상품 C", 3);

            postOutbound(created.id(), 5)
                    .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("INSUFFICIENT_STOCK")
                    .jsonPath("$.requested").isEqualTo(5)
                    .jsonPath("$.available").isEqualTo(3);

            assertThat(getProduct(created.id()).quantity()).isEqualTo(3);
        }

        @Test
        void 없는_상품이면_404이다() {
            postOutbound(Long.MAX_VALUE, 1)
                    .expectStatus().isNotFound()
                    .expectBody().jsonPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
        }

        @Test
        void 수량이_0이면_400이다() {
            ProductJson created = inbound(newCode(), "상품 C", 3);

            postOutbound(created.id(), 0)
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        }
    }
}
