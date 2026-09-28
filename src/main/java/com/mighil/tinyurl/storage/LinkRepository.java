package com.mighil.tinyurl.storage;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LinkRepository {

    private final JdbcClient jdbc;

    public LinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @throws org.springframework.dao.DuplicateKeyException if the code already exists */
    public void insert(String code, String longUrl) {
        jdbc.sql("INSERT INTO links (code, long_url) VALUES (:code, :longUrl)")
                .param("code", code)
                .param("longUrl", longUrl)
                .update();
    }

    public Optional<String> findLongUrl(String code) {
        return jdbc.sql("SELECT long_url FROM links WHERE code = :code")
                .param("code", code)
                .query(String.class)
                .optional();
    }
}
