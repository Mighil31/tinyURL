package com.mighil.tinyurl.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mighil.tinyurl.service.InvalidUrlException;
import com.mighil.tinyurl.service.LinkService;

@WebMvcTest(LinkController.class)
class LinkControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LinkService links;

    @Test
    void createReturns201WithShortUrl() throws Exception {
        when(links.create("https://example.com")).thenReturn(new LinkService.Link("abc1234", "https://example.com"));

        mvc.perform(post("/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/links/abc1234/stats"))
                .andExpect(jsonPath("$.code").value("abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc1234"))
                .andExpect(jsonPath("$.longUrl").value("https://example.com"));
    }

    @Test
    void createRejectsInvalidUrlWith400() throws Exception {
        when(links.create("ftp://x")).thenThrow(new InvalidUrlException("url must use http or https"));

        mvc.perform(post("/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"ftp://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("url must use http or https"));
    }

    @Test
    void createRejectsMalformedJsonWith400() throws Exception {
        mvc.perform(post("/links").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void redirectReturns302ToLongUrl() throws Exception {
        when(links.resolve("abc1234")).thenReturn(Optional.of("https://example.com"));

        mvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void redirectReturns404ForUnknownCode() throws Exception {
        when(links.resolve("zzzzzzz")).thenReturn(Optional.empty());

        mvc.perform(get("/zzzzzzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("link not found"));
    }
}
