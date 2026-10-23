package com.deepfine.inventory.controller;

import com.deepfine.inventory.domain.PagedResult;
import com.deepfine.inventory.domain.StockHistoryType;
import com.deepfine.inventory.dto.InboundRequest;
import com.deepfine.inventory.dto.OutboundRequest;
import com.deepfine.inventory.dto.ProductResponse;
import com.deepfine.inventory.dto.StockHistoryResponse;
import com.deepfine.inventory.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상품 재고", description = "입고, 출고, 재고 조회, 입출고 이력")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    /** 선택 헤더. 같은 키의 재요청은 다시 반영하지 않고 처음 결과를 돌려준다. */
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private static final String IDEMPOTENCY_KEY_DESCRIPTION =
            "멱등 키(64자 이하, 선택). 같은 키의 재요청은 다시 반영하지 않고 처음 반영됐을 때의 응답을 그대로 돌려준다.";

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "입고",
            description = "미등록 상품 코드면 신규 등록 후 입고하고, 등록된 코드면 수량만 더한다(이름은 기존 유지).")
    @ApiResponse(responseCode = "200", description = "입고 직후 상품 상태")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 수량 범위(1~1,000,000) 밖, 코드나 이름 누락, 본문 형식 오류, 64자를 넘는 Idempotency-Key",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/inbound")
    public ProductResponse inbound(@Valid @RequestBody InboundRequest request,
            @Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION)
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) @Size(max = 64) @Nullable String idempotencyKey) {
        return ProductResponse.from(
                productService.inbound(request.code(), request.name(), request.quantity(), idempotencyKey));
    }

    @Operation(summary = "출고", description = "재고가 충분할 때만 차감한다. 동시에 들어와도 초과 출고가 생기지 않는다.")
    @ApiResponse(responseCode = "200", description = "출고 직후 상품 상태")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 수량 범위(1~1,000,000) 밖, 본문 형식 오류, 64자를 넘는 Idempotency-Key",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND: 없는 상품 id",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "INSUFFICIENT_STOCK: 재고 부족. requested와 available 포함",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/{id}/outbound")
    public ProductResponse outbound(@PathVariable long id, @Valid @RequestBody OutboundRequest request,
            @Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION)
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) @Size(max = 64) @Nullable String idempotencyKey) {
        return ProductResponse.from(productService.outbound(id, request.quantity(), idempotencyKey));
    }

    @Operation(summary = "상품 현재 상태 조회")
    @ApiResponse(responseCode = "200", description = "상품 현재 상태")
    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND: 없는 상품 id",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable long id) {
        return ProductResponse.from(productService.get(id));
    }

    /** 상품 목록, 최근 등록순. 파라미터 제약은 Spring MVC 내장 메서드 검증이 적용해 어기면 400이다. */
    @Operation(summary = "상품 목록 조회", description = "최근 등록순 페이지.")
    @ApiResponse(responseCode = "200", description = "상품 페이지")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: page나 size 범위 오류",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public PagedResult<ProductResponse> list(
            @Parameter(description = "0부터 시작") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "1~100, 기본 20") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return productService.list(page, size).map(ProductResponse::from);
    }

    /** 입출고 이력, 최신순. */
    @Operation(summary = "입출고 이력 조회", description = "최신순 페이지. type으로 입고/출고만 거를 수 있다.")
    @ApiResponse(responseCode = "200", description = "이력 페이지")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: page, size, type 오류",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND: 없는 상품 id",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}/history")
    public PagedResult<StockHistoryResponse> history(@PathVariable long id,
            @Parameter(description = "INBOUND 또는 OUTBOUND. 없으면 전체") @RequestParam(required = false) @Nullable StockHistoryType type,
            @Parameter(description = "0부터 시작") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "1~100, 기본 20") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return productService.history(id, type, page, size).map(StockHistoryResponse::from);
    }
}
