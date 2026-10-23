package com.deepfine.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 출고 요청. 대상 상품은 경로의 id로 지정한다. */
@Schema(description = "출고 요청")
public record OutboundRequest(
        @Schema(description = "출고 수량(1~1,000,000)", example = "3")
        @NotNull @Min(1) @Max(1_000_000) Integer quantity) {
}
