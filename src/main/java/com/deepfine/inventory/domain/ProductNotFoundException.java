package com.deepfine.inventory.domain;

public class ProductNotFoundException extends RuntimeException {

    private final long productId;

    public ProductNotFoundException(long productId) {
        super("상품을 찾을 수 없습니다. id=" + productId);
        this.productId = productId;
    }

    public long getProductId() {
        return productId;
    }
}
