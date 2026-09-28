package com.mighil.tinyurl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.mighil.tinyurl.storage.LinkRepository;

class LinkServiceTest {

    private final LinkRepository repo = mock(LinkRepository.class);
    private final CodeGenerator codes = mock(CodeGenerator.class);
    private final LinkService service = new LinkService(repo, codes, new UrlValidator());

    @Test
    void retriesWithANewCodeOnCollision() {
        when(codes.next()).thenReturn("aaaaaaa", "bbbbbbb");
        doThrow(new DuplicateKeyException("dup")).when(repo).insert("aaaaaaa", "https://example.com", "owner");

        LinkService.Link link = service.create("https://example.com", "owner");

        assertThat(link.code()).isEqualTo("bbbbbbb");
        verify(repo).insert("bbbbbbb", "https://example.com", "owner");
    }

    @Test
    void givesUpAfterMaxAttempts() {
        when(codes.next()).thenReturn("aaaaaaa");
        doThrow(new DuplicateKeyException("dup")).when(repo).insert(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.create("https://example.com", "owner")).isInstanceOf(IllegalStateException.class);
        verify(repo, times(LinkService.MAX_ATTEMPTS)).insert(anyString(), anyString(), anyString());
    }

    @Test
    void invalidUrlNeverTouchesStorage() {
        assertThatThrownBy(() -> service.create("ftp://example.com", "owner")).isInstanceOf(InvalidUrlException.class);
        verify(repo, never()).insert(any(), any(), any());
    }
}
