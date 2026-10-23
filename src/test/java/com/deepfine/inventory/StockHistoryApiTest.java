package com.deepfine.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient;

class StockHistoryApiTest extends ApiTestSupport {

    record HistoryJson(long id, String type, int quantity, int quantityAfter, String createdAt) {
    }

    record HistoryPageJson(List<HistoryJson> content, int page, int size, long totalElements) {
    }

    private RestTestClient.ResponseSpec getHistory(long productId, String query) {
        return client.get().uri("/api/products/{id}/history" + query, productId).exchange();
    }

    private HistoryPageJson history(long productId, String query) {
        return getHistory(productId, query)
                .expectStatus().isOk()
                .expectBody(HistoryPageJson.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void 입출고_이력을_최신순으로_돌려준다() {
        long productId = inbound(newCode(), "이력 상품", 10).id();
        postOutbound(productId, 3).expectStatus().isOk();
        inbound(getProduct(productId).code(), "이력 상품", 5);

        HistoryPageJson page = history(productId, "");

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content()).extracting(HistoryJson::type, HistoryJson::quantity, HistoryJson::quantityAfter)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("INBOUND", 5, 12),
                        org.assertj.core.groups.Tuple.tuple("OUTBOUND", 3, 7),
                        org.assertj.core.groups.Tuple.tuple("INBOUND", 10, 10));
        assertThat(page.content()).allSatisfy(h -> assertThat(h.createdAt()).isNotBlank());
    }

    @Test
    void type으로_입고만_거를_수_있다() {
        long productId = inbound(newCode(), "이력 상품", 10).id();
        postOutbound(productId, 3).expectStatus().isOk();
        inbound(getProduct(productId).code(), "이력 상품", 5);

        HistoryPageJson page = history(productId, "?type=INBOUND");

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(HistoryJson::type).containsOnly("INBOUND");
    }

    @Test
    void page와_size로_나눠_본다() {
        long productId = inbound(newCode(), "이력 상품", 10).id();
        postOutbound(productId, 3).expectStatus().isOk();
        inbound(getProduct(productId).code(), "이력 상품", 5);

        HistoryPageJson first = history(productId, "?page=0&size=2");
        HistoryPageJson second = history(productId, "?page=1&size=2");

        assertThat(first.content()).hasSize(2);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(second.content()).hasSize(1);
        assertThat(second.content().get(0).quantityAfter()).isEqualTo(10);
    }

    @Test
    void 없는_상품이면_404이다() {
        getHistory(Long.MAX_VALUE, "")
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void size가_범위를_벗어나면_400이다() {
        long productId = inbound(newCode(), "이력 상품", 1).id();

        getHistory(productId, "?size=0")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        getHistory(productId, "?size=101")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void type이_잘못되면_400이다() {
        long productId = inbound(newCode(), "이력 상품", 1).id();

        getHistory(productId, "?type=FOO")
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }
}
