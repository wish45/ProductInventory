package com.deepfine.inventory.domain;

/** 입고나 출고 한 건의 결과: 어떤 상품이 그 직후 몇 개가 되었나. 같은 멱등 키의 재요청에는 이 값을 그대로 다시 돌려준다. */
public record StockChange(long productId, String code, String name, int quantityAfter) {

    public static StockChange of(Product product) {
        return new StockChange(product.getId(), product.getCode(), product.getName(), product.getQuantity());
    }
}
