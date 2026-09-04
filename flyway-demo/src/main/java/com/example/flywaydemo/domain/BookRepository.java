package com.example.flywaydemo.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Join-fetch the author so the controller can read {@code book.getAuthor().getName()}
     * with {@code open-in-view=false} (no lazy-loading outside a transaction).
     */
    @Query("select b from Book b join fetch b.author order by b.publishedYear")
    List<Book> findAllWithAuthor();
}