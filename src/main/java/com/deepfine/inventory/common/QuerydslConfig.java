package com.deepfine.inventory.common;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuerydslConfig {

    /** 주입되는 EntityManager는 트랜잭션에 따라 실제 인스턴스를 고르는 공유 프록시라 싱글턴 빈에 담아도 안전하다. */
    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
