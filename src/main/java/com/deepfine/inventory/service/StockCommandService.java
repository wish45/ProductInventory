package com.deepfine.inventory.service;

import com.deepfine.inventory.domain.InsufficientStockException;
import com.deepfine.inventory.domain.Product;
import com.deepfine.inventory.domain.ProductNotFoundException;
import com.deepfine.inventory.domain.StockChange;
import com.deepfine.inventory.domain.StockHistoryType;
import com.deepfine.inventory.repository.StockHistoryRepository;
import com.deepfine.inventory.repository.StockRepository;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 변경과 이력 기록을 한 트랜잭션으로 묶는다.
 * 행 잠금이 커밋까지 유지되므로 트랜잭션 안에서는 외부 호출 없이 짧게 끝낸다.
 *
 * 멱등 키가 있으면 먼저 같은 키의 이력을 찾아 있으면 그 결과를 돌려준다. 그 검사를 통과한 두 요청이
 * 동시에 들어오는 경우는 stock_history.request_id의 UNIQUE 제약이 막고, 그때의 재시도는 트랜잭션 밖(ProductService)에서 한다.
 */
@Service
public class StockCommandService {

    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;

    public StockCommandService(StockRepository stockRepository, StockHistoryRepository stockHistoryRepository) {
        this.stockRepository = stockRepository;
        this.stockHistoryRepository = stockHistoryRepository;
    }

    @Transactional
    public StockChange inbound(String code, String name, int quantity, @Nullable String requestId) {
        Optional<StockChange> replayed = findByRequestId(requestId);
        if (replayed.isPresent()) {
            return replayed.get();
        }
        Product product = stockRepository.increaseOrRegister(code, name, quantity);
        stockHistoryRepository.save(product.getId(), StockHistoryType.INBOUND, quantity, product.getQuantity(),
                requestId);
        return StockChange.of(product);
    }

    @Transactional
    public StockChange outbound(long productId, int quantity, @Nullable String requestId) {
        Optional<StockChange> replayed = findByRequestId(requestId);
        if (replayed.isPresent()) {
            return replayed.get();
        }
        Optional<Product> updated = stockRepository.decreaseIfEnough(productId, quantity);
        if (updated.isEmpty()) {
            // 실패 경로에서만 한 번 더 조회해 "없는 상품"과 "재고 부족"을 가른다
            int available = stockRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId))
                    .getQuantity();
            throw new InsufficientStockException(productId, quantity, available);
        }
        Product product = updated.get();
        stockHistoryRepository.save(productId, StockHistoryType.OUTBOUND, quantity, product.getQuantity(), requestId);
        return StockChange.of(product);
    }

    @Transactional(readOnly = true)
    public Optional<StockChange> findByRequestId(@Nullable String requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return stockHistoryRepository.findChangeByRequestId(requestId);
    }
}
