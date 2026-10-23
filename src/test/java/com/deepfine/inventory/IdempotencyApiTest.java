package com.deepfine.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Idempotency-Key 헤더: 네트워크 타임아웃 뒤 클라이언트가 재시도해도 입출고가 두 번 반영되지 않아야 한다.
 */
class IdempotencyApiTest extends ApiTestSupport {

    private static final String HEADER = "Idempotency-Key";

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private RestTestClient.ResponseSpec postInboundWithKey(String key, String code, String name, int quantity) {
        return client.post().uri("/api/products/inbound")
                .header(HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", code, "name", name, "quantity", quantity))
                .exchange();
    }

    private RestTestClient.ResponseSpec postOutboundWithKey(String key, long productId, int quantity) {
        return client.post().uri("/api/products/{id}/outbound", productId)
                .header(HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", quantity))
                .exchange();
    }

    private static ProductJson body(RestTestClient.ResponseSpec response) {
        return response.expectStatus().isOk().expectBody(ProductJson.class).returnResult().getResponseBody();
    }

    @Test
    void 같은_키로_입고를_두_번_보내면_한_번만_반영되고_같은_응답을_돌려준다() {
        String key = newKey();
        String code = newCode();

        ProductJson first = body(postInboundWithKey(key, code, "멱등", 10));
        ProductJson second = body(postInboundWithKey(key, code, "멱등", 10));

        assertThat(second).isEqualTo(first);
        assertThat(getProduct(first.id()).quantity()).isEqualTo(10);
        assertThat(countHistory(first.id(), "INBOUND")).isEqualTo(1);
    }

    @Test
    void 같은_키로_출고를_두_번_보내면_한_번만_반영되고_같은_응답을_돌려준다() {
        long productId = inbound(newCode(), "멱등", 10).id();
        String key = newKey();

        ProductJson first = body(postOutboundWithKey(key, productId, 3));
        ProductJson second = body(postOutboundWithKey(key, productId, 3));

        assertThat(first.quantity()).isEqualTo(7);
        assertThat(second).isEqualTo(first);
        assertThat(getProduct(productId).quantity()).isEqualTo(7);
        assertThat(countHistory(productId, "OUTBOUND")).isEqualTo(1);
    }

    @Test
    void 키가_다르면_각각_반영된다() {
        String code = newCode();

        ProductJson first = body(postInboundWithKey(newKey(), code, "멱등", 10));
        ProductJson second = body(postInboundWithKey(newKey(), code, "멱등", 10));

        assertThat(second.quantity()).isEqualTo(20);
        assertThat(getProduct(first.id()).quantity()).isEqualTo(20);
    }

    @Test
    void 같은_키를_동시에_20번_보내도_한_번만_반영된다() throws Exception {
        String key = newKey();
        String code = newCode();

        Map<HttpStatusCode, Long> statuses = runConcurrently(20,
                i -> postInboundWithKey(key, code, "멱등 동시", 100).returnResult(Void.class).getStatus());

        assertThat(statuses).containsOnlyKeys(HttpStatus.OK);
        long productId = jdbc.sql("SELECT id FROM product WHERE code = :code")
                .param("code", code).query(Long.class).single();
        assertThat(getProduct(productId).quantity()).isEqualTo(100);
        assertThat(countHistory(productId, "INBOUND")).isEqualTo(1);
        assertQuantityMatchesHistory(productId);
    }

    @Test
    void 키가_64자를_넘으면_400이고_헤더_이름을_알려준다() {
        postInboundWithKey("k".repeat(65), newCode(), "멱등", 1)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.errors[0].field").isEqualTo("Idempotency-Key");
    }
}
