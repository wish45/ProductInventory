package com.deepfine.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/** 입출고 이력. 재고 변경과 같은 트랜잭션에서 한 건씩 쌓이며, 수정하거나 삭제하지 않는다. */
@Entity
@Table(name = "stock_history")
public class StockHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockHistoryType type;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    /** 멱등 키(Idempotency-Key 헤더). DB의 UNIQUE 제약이 같은 요청의 두 번째 반영을 막는다. */
    @Column(name = "request_id", length = 64)
    private @Nullable String requestId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected StockHistory() {
        // JPA 전용
    }

    public StockHistory(Long productId, StockHistoryType type, int quantity, int quantityAfter,
            @Nullable String requestId) {
        this.productId = productId;
        this.type = type;
        this.quantity = quantity;
        this.quantityAfter = quantityAfter;
        this.requestId = requestId;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public StockHistoryType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getQuantityAfter() {
        return quantityAfter;
    }

    public @Nullable String getRequestId() {
        return requestId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
