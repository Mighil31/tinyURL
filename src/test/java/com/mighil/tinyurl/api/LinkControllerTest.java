package com.mighil.tinyurl.api;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.mighil.tinyurl.service.ApiKeys;
import com.mighil.tinyurl.service.InvalidUrlException;
import com.mighil.tinyurl.service.LinkService;
import com.mighil.tinyurl.service.RateLimiter;
import com.mighil.tinyurl.service.RateLimiter.Decision;

// Real ApiKeys with the local defaults from application.properties: dev-free-key (free), dev-pro-key (pro).
@WebMvcTest(LinkController.class)
@Import(ApiKeys.class)
class LinkControllerTest {

    private static final String FREE_KEY = "dev-free-key";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LinkService links;

    @MockitoBean
    RateLimiter limiter;

    @BeforeEach
    void allowByDefault() {
        when(limiter.tryAcquire(anyString(), anyInt())).thenReturn(Decision.allow());
    }

    private static MockHttpServletRequestBuilder createLink(String body) {
        return post("/links").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void createReturns201WithShortUrl() throws Exception {
        when(links.create("https://example.com", ApiKeys.fingerprint(FREE_KEY)))
                .thenReturn(new LinkService.Link("abc1234", "https://example.com"));

        mvc.perform(createLink("{\"url\":\"https://example.com\"}").header("X-API-Key", FREE_KEY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/links/abc1234/stats"))
                .andExpect(jsonPath("$.code").value("abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc1234"))
                .andExpect(jsonPath("$.longUrl").value("https://example.com"));
    }

    @Test
    void createRejectsInvalidUrlWith400() throws Exception {
        when(links.create(eq("ftp://x"), anyString())).thenThrow(new InvalidUrlException("url must use http or https"));

        mvc.perform(createLink("{\"url\":\"ftp://x\"}").header("X-API-Key", FREE_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("url must use http or https"));
    }

    @Test
    void createRejectsMalformedJsonWith400() throws Exception {
        mvc.perform(createLink("not json").header("X-API-Key", FREE_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void missingApiKeyIs401AndSkipsLimiter() throws Exception {
        mvc.perform(createLink("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.error").value("missing or invalid API key"));
        verifyNoInteractions(limiter, links);
    }

    @Test
    void unknownApiKeyIs401() throws Exception {
        mvc.perform(createLink("{\"url\":\"https://example.com\"}").header("X-API-Key", "nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing or invalid API key"));
    }

    @Test
    void authIsCheckedBeforeBodyParsing() throws Exception {
        mvc.perform(createLink("not json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void overLimitIs429WithRetryAfter() throws Exception {
        when(limiter.tryAcquire(anyString(), anyInt())).thenReturn(Decision.deny(Duration.ofMillis(29_001)));

        mvc.perform(createLink("{\"url\":\"https://example.com\"}").header("X-API-Key", FREE_KEY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30))
                .andExpect(jsonPath("$.error").value("rate limit exceeded"));
        verifyNoInteractions(links);
    }

    @Test
    void limiterUsesTheKeysTierLimit() throws Exception {
        when(links.create(anyString(), anyString())).thenReturn(new LinkService.Link("abc1234", "https://example.com"));

        mvc.perform(createLink("{\"url\":\"https://example.com\"}").header("X-API-Key", "dev-pro-key"));
        mvc.perform(createLink("{\"url\":\"https://example.com\"}").header("X-API-Key", FREE_KEY));

        verify(limiter).tryAcquire("dev-pro-key", 100);
        verify(limiter).tryAcquire(FREE_KEY, 10);
    }

    @Test
    void redirectIsPublicAndReturns302() throws Exception {
        when(links.resolve("abc1234")).thenReturn(Optional.of("https://example.com"));

        mvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
        verifyNoInteractions(limiter);
    }

    @Test
    void redirectReturns404ForUnknownCode() throws Exception {
        when(links.resolve("zzzzzzz")).thenReturn(Optional.empty());

        mvc.perform(get("/zzzzzzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("link not found"));
    }
}
