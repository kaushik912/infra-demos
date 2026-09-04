package com.example.liquibasedemo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.liquibasedemo.domain.AuthorRepository;
import com.example.liquibasedemo.domain.BookRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end check: booting the context runs Liquibase against H2 (contexts=dev),
 * then Hibernate validates the entities against the changelog-produced schema.
 */
@SpringBootTest
class LiquibaseDemoApplicationTests {

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
    void changelogSeededData() {
        assertThat(authors.count()).isEqualTo(2); // v4 ran because context=dev is active
        assertThat(books.count()).isEqualTo(4);
    }

    @Test
    void liquibaseRecordedEveryChangeSet() {
        // Liquibase names its tracking table DATABASECHANGELOG (uppercase, unquoted).
        Integer applied = jdbc.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG", Integer.class);
        assertThat(applied).isEqualTo(9); // v1(1) + v2(3) + v3(2) + v4(2) + v5(1)
    }

    @Test
    void runOnChangeViewIsQueryable() {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM book_catalog", Integer.class);
        assertThat(rows).isEqualTo(4);
    }
}