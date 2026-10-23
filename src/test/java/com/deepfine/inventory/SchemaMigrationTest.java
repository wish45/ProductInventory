package com.deepfine.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Flyway V1 마이그레이션이 설계한 스키마를 실제 PostgreSQL에 만드는지 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaMigrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void flyway가_product와_stock_history_테이블을_만든다() {
        List<String> tables = jdbc.sql("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public' ORDER BY table_name
                """)
                .query(String.class)
                .list();

        assertThat(tables).contains("product", "stock_history");
    }

    @Test
    void 재고는_DB_제약으로도_음수가_될_수_없다() {
        assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO product (code, name, quantity) VALUES ('NEG-1', '음수 테스트', -1)")
                .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
