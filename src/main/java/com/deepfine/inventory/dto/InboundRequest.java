package com.deepfine.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 입고 요청. 상품 코드가 입고 대상 상품을 식별하는 키다. */
@Schema(description = "입고 요청")
public record InboundRequest(
        @Schema(description = "상품 코드. 입고 대상 상품을 식별하는 키", example = "SKU-1")
        @NotBlank @Size(max = 50) String code,
        @Schema(description = "상품명. 이미 등록된 코드면 무시되고 기존 이름이 유지된다", example = "상품 A")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "입고 수량(1~1,000,000)", example = "10")
        @NotNull @Min(1) @Max(1_000_000) Integer quantity) {
}
