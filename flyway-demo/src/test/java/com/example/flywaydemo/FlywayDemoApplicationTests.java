package com.example.flywaydemo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.flywaydemo.domain.AuthorRepository;
import com.example.flywaydemo.domain.BookRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * This is a genuine end-to-end check: booting the context runs Flyway against
 * H2, then Hibernate validates the entities against the migrated schema. If any
 * migration or mapping is wrong, the context fails to start and the test fails.
 */
@SpringBootTest
class FlywayDemoApplicationTests {

    @Autowired
    AuthorRepository authors;
    @Autowired
    BookRepository books;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoads() {
    }

    @Test
    void migrationsSeededData() {
        assertThat(authors.count()).isEqualTo(2);
        assertThat(books.count()).isEqualTo(4);
    }

    @Test
    void flywayRecordedEveryVersionedMigration() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE AND \"version\" IS NOT NULL",
                Integer.class);
        assertThat(applied).isEqualTo(4); // V1..V4
    }

    @Test
    void repeatableViewIsQueryable() {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM book_catalog", Integer.class);
        assertThat(rows).isEqualTo(4);
    }
}