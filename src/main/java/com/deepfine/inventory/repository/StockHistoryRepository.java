package com.deepfine.inventory.repository;

import com.deepfine.inventory.domain.PagedResult;
import com.deepfine.inventory.domain.QProduct;
import com.deepfine.inventory.domain.QStockHistory;
import com.deepfine.inventory.domain.StockChange;
import com.deepfine.inventory.domain.StockHistory;
import com.deepfine.inventory.domain.StockHistoryType;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/** 입출고 이력의 기록과 조회. */
@Repository
public class StockHistoryRepository {

    private static final QStockHistory HISTORY = QStockHistory.stockHistory;
    private static final QProduct PRODUCT = QProduct.product;

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public StockHistoryRepository(EntityManager entityManager, JPAQueryFactory queryFactory) {
        this.entityManager = entityManager;
        this.queryFactory = queryFactory;
    }

    /**
     * 이력을 남긴다. 재고 변경과 같은 트랜잭션 안에서 호출해야 한다.
     * requestId가 이미 있으면 UNIQUE 제약 위반으로 실패하고 트랜잭션 전체(재고 변경 포함)가 되돌려진다.
     */
    public void save(long productId, StockHistoryType type, int quantity, int quantityAfter,
            @Nullable String requestId) {
        entityManager.persist(new StockHistory(productId, type, quantity, quantityAfter, requestId));
    }

    /** 멱등 키로 처음 반영된 결과를 찾는다. 상품과 조인해 그때의 quantity_after를 현재 상품 정보와 함께 돌려준다. */
    public Optional<StockChange> findChangeByRequestId(String requestId) {
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(StockChange.class,
                        PRODUCT.id, PRODUCT.code, PRODUCT.name, HISTORY.quantityAfter))
                .from(HISTORY)
                .join(PRODUCT).on(PRODUCT.id.eq(HISTORY.productId))
                .where(HISTORY.requestId.eq(requestId))
                .fetchOne());
    }

    /**
     * 상품의 이력을 최신순으로 페이지 조회한다. type이 null이면 전체.
     * 순서는 id 기준이다. created_at의 now()는 트랜잭션 시작 시각이라 잠금을 기다린 뒤 반영된 순서와
     * 어긋날 수 있지만, id는 재고 행 잠금을 잡은 뒤 발급되므로 실제 반영 순서와 같다.
     */
    public PagedResult<StockHistory> findPage(long productId, @Nullable StockHistoryType type, int page, int size) {
        BooleanExpression condition = HISTORY.productId.eq(productId).and(typeEq(type));

        List<StockHistory> content = queryFactory.selectFrom(HISTORY)
                .where(condition)
                .orderBy(HISTORY.id.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch();
        Long total = queryFactory.select(HISTORY.count())
                .from(HISTORY)
                .where(condition)
                .fetchOne();

        return new PagedResult<>(content, page, size, total == null ? 0 : total);
    }

    /** null을 돌려주면 QueryDSL이 조건에서 뺀다. 동적 조건 만들 때 흔히 쓰는 방식. */
    private static @Nullable BooleanExpression typeEq(@Nullable StockHistoryType type) {
        return type == null ? null : HISTORY.type.eq(type);
    }
}
