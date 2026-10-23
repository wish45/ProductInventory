-- 재고 관리 시스템 MVP 초기 스키마 (PostgreSQL)

-- 상품. quantity는 조회용 파생 값이고 기준은 stock_history다
CREATE TABLE product (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,                 -- 입고 요청의 상품 식별 키
    name        VARCHAR(100) NOT NULL,
    quantity    INTEGER      NOT NULL DEFAULT 0 CHECK (quantity >= 0), -- 음수 재고의 최종 방어선
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 입출고 이력: 재고 변경과 같은 트랜잭션에서 기록한다
CREATE TABLE stock_history (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      BIGINT      NOT NULL REFERENCES product (id),
    type            VARCHAR(10) NOT NULL CHECK (type IN ('INBOUND', 'OUTBOUND')),
    quantity        INTEGER     NOT NULL CHECK (quantity > 0),
    quantity_after  INTEGER     NOT NULL,                     -- 변경 직후 재고 수량
    request_id      VARCHAR(64) UNIQUE,                       -- 멱등 키 (Idempotency-Key 헤더). 같은 키의 동시 재요청은 이 제약이 막는다
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 이력 조회는 id 순이다. created_at의 now()는 트랜잭션 시작 시각이라 잠금 대기 후 반영된 순서와 어긋날 수 있지만 id는 반영 순서와 같다
CREATE INDEX idx_stock_history_product ON stock_history (product_id, id);
