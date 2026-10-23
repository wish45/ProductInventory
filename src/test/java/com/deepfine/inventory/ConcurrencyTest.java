package com.deepfine.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * 동시 요청에서의 재고 정합성 증명.
 * 정합성 증명이지 처리량 측정이 아니다: DB에 동시에 들어가는 요청 수는 커넥션 풀 크기(기본 10)에 묶인다.
 */
class ConcurrencyTest extends ApiTestSupport {

    @Test
    void 재고_100에_1개씩_출고_1000건을_동시에_보내도_초과_출고가_없다() throws Exception {
        long productId = inbound(newCode(), "동시 출고", 100).id();

        Map<HttpStatusCode, Long> statuses = runConcurrently(1_000,
                i -> postOutbound(productId, 1).returnResult(Void.class).getStatus());

        assertThat(statuses.getOrDefault(HttpStatus.OK, 0L)).isEqualTo(100);
        assertThat(statuses.getOrDefault(HttpStatus.CONFLICT, 0L)).isEqualTo(900);
        assertThat(getProduct(productId).quantity()).isZero();
        assertThat(countHistory(productId, "OUTBOUND")).isEqualTo(100);
        assertQuantityMatchesHistory(productId);
    }

    @Test
    void 같은_신규_상품_코드로_입고_100건을_동시에_보내도_상품은_1행이고_수량은_합계다() throws Exception {
        String code = newCode();
        int expectedTotal = IntStream.rangeClosed(1, 100).sum(); // 5050

        Map<HttpStatusCode, Long> statuses = runConcurrently(100,
                i -> postInbound(code, "동시 입고", i + 1).returnResult(Void.class).getStatus());

        assertThat(statuses.getOrDefault(HttpStatus.OK, 0L)).isEqualTo(100);
        long rows = jdbc.sql("SELECT count(*) FROM product WHERE code = :code")
                .param("code", code).query(Long.class).single();
        assertThat(rows).isEqualTo(1);
        long productId = jdbc.sql("SELECT id FROM product WHERE code = :code")
                .param("code", code).query(Long.class).single();
        assertThat(getProduct(productId).quantity()).isEqualTo(expectedTotal);
        assertThat(countHistory(productId, "INBOUND")).isEqualTo(100);
        assertQuantityMatchesHistory(productId);
    }

    /**
     * 입고와 출고가 섞여 들어올 때. 출고 성공 수는 실행 순서에 따라 달라지므로 고정값 대신
     * "최종 재고 = 초기 + 입고 합 - 성공한 출고 합"과 이력 대조로 검증한다.
     */
    @Test
    void 입고와_출고가_동시에_섞여_들어와도_최종_재고는_초기_더하기_입고_빼기_성공한_출고다() throws Exception {
        int initial = 50;
        int inbounds = 100;   // 1개씩
        int outbounds = 200;  // 1개씩
        String code = newCode();
        long productId = inbound(code, "혼합", initial).id();

        Map<HttpStatusCode, Long> statuses = runConcurrently(inbounds + outbounds, i -> i < inbounds
                ? postInbound(code, "혼합", 1).returnResult(Void.class).getStatus()
                : postOutbound(productId, 1).returnResult(Void.class).getStatus());

        long outboundOk = statuses.getOrDefault(HttpStatus.OK, 0L) - inbounds;
        long outboundConflict = statuses.getOrDefault(HttpStatus.CONFLICT, 0L);
        assertThat(statuses.keySet()).as("200/409 외의 응답은 없어야 한다")
                .containsOnly(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(outboundOk + outboundConflict).isEqualTo(outbounds);
        assertThat(getProduct(productId).quantity()).isEqualTo(initial + inbounds - outboundOk);
        assertThat(countHistory(productId, "INBOUND")).isEqualTo(1 + inbounds);
        assertThat(countHistory(productId, "OUTBOUND")).isEqualTo(outboundOk);
        assertQuantityMatchesHistory(productId);
    }
}
