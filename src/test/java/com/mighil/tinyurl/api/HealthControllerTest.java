package com.mighil.tinyurl.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mighil.tinyurl.service.ApiKeys;
import com.mighil.tinyurl.service.RateLimiter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    MockMvc mvc;

    // the slice picks up ApiKeyInterceptor, which needs these
    @MockitoBean
    ApiKeys apiKeys;

    @MockitoBean
    RateLimiter limiter;

    @Test
    void healthzReturns200WithNoBody() throws Exception {
        mvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }
}
