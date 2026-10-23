package com.deepfine.inventory.domain;

import java.util.List;
import java.util.function.Function;

/** 페이지 조회 결과. page는 0부터 시작한다. */
public record PagedResult<T>(List<T> content, int page, int size, long totalElements) {

    public <R> PagedResult<R> map(Function<T, R> mapper) {
        return new PagedResult<>(content.stream().map(mapper).toList(), page, size, totalElements);
    }
}
