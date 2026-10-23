package com.deepfine.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 실제 서버(임의 포트) + 실제 PostgreSQL(Testcontainers)로 API를 호출하는 테스트의 공통 설정.
 * 테스트 데이터는 입고 API로만 만들고 DB에 직접 INSERT하지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
abstract class ApiTestSupport {

    /** 응답 JSON을 받는 테스트 전용 타입. 프로덕션 코드에 의존하지 않는다. */
    record ProductJson(long id, String code, String name, int quantity) {
    }

    /**
     * 동시에 열어 두는 HTTP 연결 수. 톰캣 기본 워커 스레드 수와 같다.
     * 1,000개 연결을 한순간에 열면 OS 연결 대기열(backlog)을 넘쳐 환경에 따라 연결이 거절되는데,
     * 그것은 DB 정합성과 무관한 실패라서 연결 수만 제한한다. 요청은 모두 같은 출발선에서 시작한다.
     */
    private static final int MAX_IN_FLIGHT = 200;

    @Autowired
    protected RestTestClient client;

    @Autowired
    protected JdbcClient jdbc;

    /** 테스트끼리 데이터가 섞이지 않도록 매번 새 상품 코드를 만든다. */
    protected static String newCode() {
        return "SKU-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected RestTestClient.ResponseSpec postInbound(String code, String name, Object quantity) {
        return client.post().uri("/api/products/inbound")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", code, "name", name, "quantity", quantity))
                .exchange();
    }

    protected ProductJson inbound(String code, String name, int quantity) {
        return postInbound(code, name, quantity)
                .expectStatus().isOk()
                .expectBody(ProductJson.class)
                .returnResult()
                .getResponseBody();
    }

    protected RestTestClient.ResponseSpec postOutbound(long productId, Object quantity) {
        return client.post().uri("/api/products/{id}/outbound", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", quantity))
                .exchange();
    }

    protected ProductJson getProduct(long productId) {
        return client.get().uri("/api/products/{id}", productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductJson.class)
                .returnResult()
                .getResponseBody();
    }

    protected long countHistory(long productId, String type) {
        return jdbc.sql("SELECT count(*) FROM stock_history WHERE product_id = :id AND type = :type")
                .param("id", productId)
                .param("type", type)
                .query(Long.class)
                .single();
    }

    /**
     * 대조 검증: 이력이 기준이고 현재 수량은 파생 값이므로,
     * 현재 수량 = 입고 합 - 출고 합이고 마지막 이력의 quantity_after가 현재 수량과 같아야 한다.
     */
    protected void assertQuantityMatchesHistory(long productId) {
        Boolean matchesSum = jdbc.sql("""
                SELECT p.quantity = COALESCE(SUM(CASE h.type WHEN 'INBOUND' THEN h.quantity ELSE -h.quantity END), 0)
                  FROM product p LEFT JOIN stock_history h ON h.product_id = p.id
                 WHERE p.id = :id
                 GROUP BY p.quantity
                """)
                .param("id", productId)
                .query(Boolean.class)
                .single();
        assertThat(matchesSum).as("현재 수량 = 입고 합 - 출고 합").isTrue();

        Integer lastQuantityAfter = jdbc.sql("""
                SELECT quantity_after FROM stock_history
                 WHERE product_id = :id ORDER BY id DESC LIMIT 1
                """)
                .param("id", productId)
                .query(Integer.class)
                .single();
        assertThat(lastQuantityAfter).as("마지막 이력의 quantity_after = 현재 수량")
                .isEqualTo(getProduct(productId).quantity());
    }

    /** 가상 스레드 count개를 CountDownLatch로 같은 출발선에 세운 뒤 동시에 호출하고 상태 코드별 개수를 센다. */
    protected Map<HttpStatusCode, Long> runConcurrently(int count, IntFunction<HttpStatusCode> call)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<HttpStatusCode>> futures = IntStream.range(0, count)
                    .mapToObj(i -> (Callable<HttpStatusCode>) () -> {
                        ready.countDown();
                        start.await();
                        inFlight.acquire();
                        try {
                            return call.apply(i);
                        } finally {
                            inFlight.release();
                        }
                    })
                    .map(executor::submit)
                    .toList();
            ready.await();
            start.countDown();

            Map<HttpStatusCode, Long> result = new HashMap<>();
            for (Future<HttpStatusCode> future : futures) {
                result.merge(future.get(), 1L, Long::sum);
            }
            return result;
        }
    }
}
