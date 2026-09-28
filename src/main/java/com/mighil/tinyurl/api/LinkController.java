package com.mighil.tinyurl.api;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mighil.tinyurl.service.LinkService;
import com.mighil.tinyurl.service.LinkService.Link;

@RestController
public class LinkController {

    private final LinkService links;
    private final String baseUrl;

    public LinkController(LinkService links, @Value("${app.base-url}") String baseUrl) {
        this.links = links;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @PostMapping("/links")
    public ResponseEntity<LinkResponse> create(
            @RequestAttribute(ApiKeyInterceptor.OWNER_ATTRIBUTE) String owner,
            @RequestBody CreateLinkRequest request) {
        Link link = links.create(request.url(), owner);
        return ResponseEntity.created(URI.create(baseUrl + "/links/" + link.code() + "/stats"))
                .body(new LinkResponse(link.code(), baseUrl + "/" + link.code(), link.longUrl()));
    }

    // Only well-formed codes reach the DB; anything else falls through to the default 404.
    @GetMapping("/{code:[0-9A-Za-z]{7}}")
    public ResponseEntity<?> redirect(@PathVariable String code) {
        return links.resolve(code)
                .<ResponseEntity<?>>map(url -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("link not found")));
    }
}
