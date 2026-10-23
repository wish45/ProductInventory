package com.deepfine.inventory.repository;

import com.deepfine.inventory.domain.PagedResult;
import com.deepfine.inventory.domain.Product;
import com.deepfine.inventory.domain.QProduct;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 재고 숫자를 바꾸는 SQL은 모두 이 클래스에만 둔다.
 * 엔티티를 읽고 계산해서 쓰는 대신 UPDATE/UPSERT 한 문장으로 바꿔서, 락 없이도 동시 요청에서 정합성을 지킨다.
 * 재고를 바꾸는 두 문장은 RETURNING으로 왕복 1회에 끝내기 위해 네이티브 SQL이고, 조회는 QueryDSL이다.
 */
@Repository
public class StockRepository {

    private static final QProduct PRODUCT = QProduct.product;

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public StockRepository(EntityManager entityManager, JPAQueryFactory queryFactory) {
        this.entityManager = entityManager;
        this.queryFactory = queryFactory;
    }

    /**
     * 입고: 미등록 코드면 신규 등록, 등록된 코드면 수량만 더한다(이름은 기존 유지).
     * INSERT … ON CONFLICT DO UPDATE는 동시에 들어와도 INSERT 또는 UPDATE 중 하나로 원자적으로 끝난다.
     * JPQL/QueryDSL에는 UPSERT가 없어 네이티브 SQL로 쓰고, RETURNING *로 엔티티에 매핑한다.
     */
    public Product increaseOrRegister(String code, String name, int quantity) {
        return (Product) entityManager.createNativeQuery("""
                INSERT INTO product (code, name, quantity)
                VALUES (:code, :name, :quantity)
                ON CONFLICT (code) DO UPDATE
                   SET quantity = product.quantity + EXCLUDED.quantity, updated_at = now()
                RETURNING *
                """, Product.class)
                .setParameter("code", code)
                .setParameter("name", name)
                .setParameter("quantity", quantity)
                .getSingleResult();
    }

    /**
     * 출고: 재고가 충분할 때만 차감하는 조건부 UPDATE. 비어 있으면 상품이 없거나 재고가 부족한 것이다.
     * READ COMMITTED에서 같은 행을 기다린 뒤 WHERE를 다시 평가하므로 동시에 들어와도 초과 출고가 생기지 않는다.
     * JPQL에는 RETURNING이 없어 네이티브 SQL로 쓰고, 갱신된 행을 바로 받아 행 잠금을 쥔 채 다시 읽지 않는다.
     */
    public Optional<Product> decreaseIfEnough(long productId, int quantity) {
        List<?> updated = entityManager.createNativeQuery("""
                UPDATE product
                   SET quantity = quantity - :quantity, updated_at = now()
                 WHERE id = :id AND quantity >= :quantity
                RETURNING *
                """, Product.class)
                .setParameter("id", productId)
                .setParameter("quantity", quantity)
                .getResultList();
        return updated.stream().findFirst().map(Product.class::cast);
    }

    public Optional<Product> findById(long productId) {
        return Optional.ofNullable(queryFactory.selectFrom(PRODUCT)
                .where(PRODUCT.id.eq(productId))
                .fetchOne());
    }

    /** 상품 목록, 최근 등록순. */
    public PagedResult<Product> findPage(int page, int size) {
        List<Product> content = queryFactory.selectFrom(PRODUCT)
                .orderBy(PRODUCT.id.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch();
        Long total = queryFactory.select(PRODUCT.count())
                .from(PRODUCT)
                .fetchOne();
        return new PagedResult<>(content, page, size, total == null ? 0 : total);
    }
}
