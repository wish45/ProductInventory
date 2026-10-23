package com.deepfine.inventory.service;

import com.deepfine.inventory.domain.PagedResult;
import com.deepfine.inventory.domain.Product;
import com.deepfine.inventory.domain.ProductNotFoundException;
import com.deepfine.inventory.domain.StockChange;
import com.deepfine.inventory.domain.StockHistory;
import com.deepfine.inventory.domain.StockHistoryType;
import com.deepfine.inventory.repository.StockHistoryRepository;
import com.deepfine.inventory.repository.StockRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 유스케이스의 진입점. 재고 변경은 StockCommandService의 트랜잭션에 맡기고,
 * 같은 멱등 키가 동시에 들어와 UNIQUE 제약에 걸린 경우의 재조회만 트랜잭션 밖인 여기서 한다.
 * (롤백된 트랜잭션 안에서는 다시 조회할 수 없기 때문이다.)
 */
@Service
public class ProductService {

    private final StockCommandService stockCommandService;
    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;

    public ProductService(StockCommandService stockCommandService, StockRepository stockRepository,
            StockHistoryRepository stockHistoryRepository) {
        this.stockCommandService = stockCommandService;
        this.stockRepository = stockRepository;
        this.stockHistoryRepository = stockHistoryRepository;
    }

    public StockChange inbound(String code, String name, int quantity, @Nullable String requestId) {
        try {
            return stockCommandService.inbound(code, name, quantity, requestId);
        } catch (DataIntegrityViolationException e) {
            return replayOrRethrow(requestId, e);
        }
    }

    public StockChange outbound(long productId, int quantity, @Nullable String requestId) {
        try {
            return stockCommandService.outbound(productId, quantity, requestId);
        } catch (DataIntegrityViolationException e) {
            return replayOrRethrow(requestId, e);
        }
    }

    /** 같은 키의 결과가 이미 있으면 그것이 제약 위반의 원인이므로 돌려주고, 없으면 다른 원인이니 그대로 던진다. */
    private StockChange replayOrRethrow(@Nullable String requestId, DataIntegrityViolationException cause) {
        return stockCommandService.findByRequestId(requestId).orElseThrow(() -> cause);
    }

    @Transactional(readOnly = true)
    public Product get(long productId) {
        return stockRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional(readOnly = true)
    public PagedResult<Product> list(int page, int size) {
        return stockRepository.findPage(page, size);
    }

    @Transactional(readOnly = true)
    public PagedResult<StockHistory> history(long productId, @Nullable StockHistoryType type, int page, int size) {
        if (stockRepository.findById(productId).isEmpty()) {
            throw new ProductNotFoundException(productId);
        }
        return stockHistoryRepository.findPage(productId, type, page, size);
    }
}
