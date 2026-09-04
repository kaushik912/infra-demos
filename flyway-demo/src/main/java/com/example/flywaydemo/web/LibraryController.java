package com.example.flywaydemo.web;

import java.util.List;

import com.example.flywaydemo.domain.Author;
import com.example.flywaydemo.domain.AuthorRepository;
import com.example.flywaydemo.domain.Book;
import com.example.flywaydemo.domain.BookRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tiny REST layer so you can SEE the migrated schema through the app:
 *   GET  /api/authors        -> rows from the `author` table (V1 + V3 columns)
 *   GET  /api/books          -> rows via the join-fetch (V2 FK to author)
 *   POST /api/authors        -> prove writes work against the migrated schema
 */
@RestController
@RequestMapping("/api")
public class LibraryController {

    private final AuthorRepository authors;
    private final BookRepository books;

    public LibraryController(AuthorRepository authors, BookRepository books) {
        this.authors = authors;
        this.books = books;
    }

    public record AuthorView(Long id, String name, String email) {
    }

    public record BookView(Long id, String title, Integer publishedYear, String authorName) {
    }

    public record NewAuthor(String name, String email) {
    }

    @GetMapping("/authors")
    public List<AuthorView> listAuthors() {
        return authors.findAll().stream()
                .map(a -> new AuthorView(a.getId(), a.getName(), a.getEmail()))
                .toList();
    }

    @GetMapping("/books")
    public List<BookView> listBooks() {
        return books.findAllWithAuthor().stream()
                .map(b -> new BookView(b.getId(), b.getTitle(), b.getPublishedYear(),
                        b.getAuthor().getName()))
                .toList();
    }

    @PostMapping("/authors")
    public AuthorView create(@RequestBody NewAuthor body) {
        Author saved = authors.save(new Author(body.name(), body.email()));
        return new AuthorView(saved.getId(), saved.getName(), saved.getEmail());
    }
}