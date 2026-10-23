package com.deepfine.inventory.dto;

import com.deepfine.inventory.domain.Product;
import com.deepfine.inventory.domain.StockChange;
import io.swagger.v3.oas.annotations.media.Schema;

/** 상품의 현재 상태 응답. 엔티티를 직접 노출하지 않는다. */
@Schema(description = "상품의 현재 상태")
public record ProductResponse(
        @Schema(description = "상품 id", example = "1") long id,
        @Schema(description = "상품 코드", example = "SKU-1") String code,
        @Schema(description = "상품명", example = "상품 A") String name,
        @Schema(description = "현재 재고 수량", example = "7") int quantity) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getCode(), product.getName(), product.getQuantity());
    }

    /** 입고나 출고 직후의 상태. 멱등 재요청이면 처음 반영됐을 때의 수량이다. */
    public static ProductResponse from(StockChange change) {
        return new ProductResponse(change.productId(), change.code(), change.name(), change.quantityAfter());
    }
}
