package com.deepfine.inventory.dto;

import com.deepfine.inventory.domain.StockHistory;
import com.deepfine.inventory.domain.StockHistoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** 입출고 이력 한 건. */
@Schema(description = "입출고 이력 한 건")
public record StockHistoryResponse(
        @Schema(description = "이력 id. 반영 순서와 같다", example = "3") long id,
        @Schema(description = "INBOUND 또는 OUTBOUND") StockHistoryType type,
        @Schema(description = "변경 수량", example = "5") int quantity,
        @Schema(description = "변경 직후 재고 수량", example = "12") int quantityAfter,
        @Schema(description = "기록 시각") OffsetDateTime createdAt) {

    public static StockHistoryResponse from(StockHistory history) {
        return new StockHistoryResponse(history.getId(), history.getType(), history.getQuantity(),
                history.getQuantityAfter(), history.getCreatedAt());
    }
}
