package com.deepfine.inventory;

import org.junit.jupiter.api.Test;

/** OpenAPI 문서(springdoc)가 떠 있고 다섯 API가 모두 실려 있는지 확인한다. */
class OpenApiDocsTest extends ApiTestSupport {

    @Test
    void api_docs에_다섯_API가_모두_실려_있다() {
        client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.info.title").isNotEmpty()
                .jsonPath("$.paths['/api/products/inbound'].post").exists()
                .jsonPath("$.paths['/api/products/{id}/outbound'].post").exists()
                .jsonPath("$.paths['/api/products'].get").exists()
                .jsonPath("$.paths['/api/products/{id}'].get").exists()
                .jsonPath("$.paths['/api/products/{id}/history'].get").exists();
    }

    @Test
    void swagger_ui가_열린다() {
        client.get().uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }
}
