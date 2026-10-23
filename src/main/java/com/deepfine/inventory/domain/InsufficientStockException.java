package com.deepfine.inventory.domain;

public class InsufficientStockException extends RuntimeException {

    private final long productId;
    private final int requested;
    private final int available;

    public InsufficientStockException(long productId, int requested, int available) {
        super("재고가 부족합니다. id=" + productId + ", 요청=" + requested + ", 현재=" + available);
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public long getProductId() {
        return productId;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
