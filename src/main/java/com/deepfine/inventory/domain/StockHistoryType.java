package com.deepfine.inventory.domain;

/** 재고 변경 종류. stock_history.type의 CHECK 제약과 일치해야 한다. */
public enum StockHistoryType {
    INBOUND, OUTBOUND
}
