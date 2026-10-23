package com.deepfine.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListApiTest extends ApiTestSupport {

    record ProductPageJson(List<ProductJson> content, int page, int size, long totalElements) {
    }

    private ProductPageJson list(String query) {
        return client.get().uri("/api/products" + query)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductPageJson.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void 목록은_최근_등록순으로_페이지로_돌려준다() {
        ProductJson older = inbound(newCode(), "먼저 등록", 1);
        ProductJson newer = inbound(newCode(), "나중 등록", 2);

        ProductPageJson page = list("?page=0&size=2");

        assertThat(page.content()).containsExactly(newer, older);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void 두_번째_페이지는_그_다음_상품부터다() {
        inbound(newCode(), "A", 1);
        ProductJson second = inbound(newCode(), "B", 1);
        inbound(newCode(), "C", 1);

        ProductPageJson page = list("?page=1&size=1");

        assertThat(page.content()).containsExactly(second);
    }

    @Test
    void size가_범위를_벗어나면_400이고_어떤_파라미터가_틀렸는지_알려준다() {
        client.get().uri("/api/products?size=101")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.errors[0].field").isEqualTo("size");
    }
}
