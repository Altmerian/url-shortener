package com.pshakhlovich.coding.urlshortener.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class HashBasedUrlShortenerTest {
    
    private HashBasedUrlShortener urlShortener;
    
    @BeforeEach
    void setUp() {
        urlShortener = new HashBasedUrlShortener();
    }
    
    @Test
    void shouldGenerateCorrectShortCodeFormat() {
        String originalUrl = "https://example.com/very/long/path/to/resource";
        String shortUrl = urlShortener.shortenUrl(originalUrl);
        
        String shortCode = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
        
        assertThat(shortCode)
            .hasSize(8)
            .matches("^[0-9A-Za-z]+$");
    }
    
    @Test
    void shouldBeDeterministic() {
        String originalUrl = "https://example.com/page";
        
        String shortUrl1 = urlShortener.shortenUrl(originalUrl);
        String shortUrl2 = urlShortener.shortenUrl(originalUrl);
        String shortUrl3 = urlShortener.shortenUrl(originalUrl);
        
        assertThat(shortUrl1)
            .isEqualTo(shortUrl2)
            .isEqualTo(shortUrl3);
    }
    
    @Test
    void shouldGenerateDifferentCodesForDifferentUrls() {
        String url1 = "https://example.com/page1";
        String url2 = "https://example.com/page2";
        
        String shortUrl1 = urlShortener.shortenUrl(url1);
        String shortUrl2 = urlShortener.shortenUrl(url2);
        
        assertThat(shortUrl1).isNotEqualTo(shortUrl2);
    }
    
    @Test
    void shouldNormalizeUrls() {
        String url1 = "https://example.com/page";
        String url2 = "https://example.com/page/";
        
        String shortUrl1 = urlShortener.shortenUrl(url1);
        String shortUrl2 = urlShortener.shortenUrl(url2);
        
        assertThat(shortUrl1).isEqualTo(shortUrl2);
    }
    
    @Test
    void shouldRetrieveOriginalUrl() {
        String originalUrl = "https://example.com/original";
        
        String shortUrl = urlShortener.shortenUrl(originalUrl);
        String retrieved = urlShortener.getOriginalUrl(shortUrl);
        
        assertThat(retrieved).isEqualTo(originalUrl);
    }
    
    @Test
    void shouldHandleBothFullUrlAndShortCode() {
        String originalUrl = "https://example.com/test";
        String shortUrl = urlShortener.shortenUrl(originalUrl);
        
        String shortCode = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
        
        String retrieved1 = urlShortener.getOriginalUrl(shortUrl);
        String retrieved2 = urlShortener.getOriginalUrl(shortCode);
        
        assertThat(retrieved1).isEqualTo(originalUrl);
        assertThat(retrieved2).isEqualTo(originalUrl);
    }
    
    @Test
    void shouldSupportBidirectionalLookup() {
        String originalUrl = "https://example.com/test";
        String shortUrl = urlShortener.shortenUrl(originalUrl);
        String expectedCode = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
        
        Optional<String> shortCode = urlShortener.getShortCode(originalUrl);
        
        assertThat(shortCode)
            .isPresent()
            .hasValue(expectedCode);
    }
    
    @Test
    void shouldRejectNullUrl() {
        assertThatThrownBy(() -> urlShortener.shortenUrl(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be null");
    }
    
    @Test
    void shouldRejectBlankUrl() {
        assertThatThrownBy(() -> urlShortener.shortenUrl("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be null or blank");
    }
    
    @Test
    void shouldRejectMalformedUrl() {
        assertThatThrownBy(() -> urlShortener.shortenUrl("not-a-valid-url"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid URL format");
    }
    
    @Test
    void shouldRejectUrlExceedingMaxLength() {
        var config = HashBasedUrlShortener.UrlShortenerConfig.builder()
            .maxUrlLength(50)
            .build();
        var shortener = new HashBasedUrlShortener(config);
        
        String tooLongUrl = "https://example.com/" + "a".repeat(100);
        
        assertThatThrownBy(() -> shortener.shortenUrl(tooLongUrl))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void shouldHandleConcurrentRequestsSafely() throws InterruptedException {
        int threadCount = 100;
        String originalUrl = "https://example.com/concurrent";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> urlShortener.shortenUrl(originalUrl)));
        }

        List<String> results = new ArrayList<>();
        for (Future<String> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                fail("Thread failed with exception", e);
            }
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(results)
            .hasSize(threadCount)
            .allMatch(result -> result.equals(results.get(0)));

        assertThat(urlShortener.size()).isEqualTo(1);
    }

    @Test
    void shouldUseCustomConfiguration() {
        var config = HashBasedUrlShortener.UrlShortenerConfig.builder()
            .domain("custom.short")
            .scheme("http")
            .build();

        var shortener = new HashBasedUrlShortener(config);
        String shortUrl = shortener.shortenUrl("https://example.com/test");

        assertThat(shortUrl)
            .startsWith("http://")
            .contains("custom.short");
    }

    @Test
    void shouldUseCustomShortCodeLength() {
        var config = HashBasedUrlShortener.UrlShortenerConfig.builder()
            .shortCodeLength(6)
            .build();

        var shortener = new HashBasedUrlShortener(config);
        String shortUrl = shortener.shortenUrl("https://example.com/test");
        String shortCode = shortener.getShortCode("https://example.com/test").orElseThrow();

        assertThat(shortCode).hasSize(6);
        assertThat(shortUrl).endsWith("/" + shortCode);
    }
}
