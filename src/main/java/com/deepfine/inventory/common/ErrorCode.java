package com.deepfine.inventory.common;

/** 에러 응답(ProblemDetail)의 code 필드. 클라이언트는 HTTP 상태 대신 이 값으로 분기한다. */
public enum ErrorCode {
    INVALID_REQUEST,
    PRODUCT_NOT_FOUND,
    INSUFFICIENT_STOCK,
    /** 예상하지 못한 서버 오류. 내부 사정은 로그에만 남기고 응답에는 넣지 않는다. */
    INTERNAL_ERROR
}
