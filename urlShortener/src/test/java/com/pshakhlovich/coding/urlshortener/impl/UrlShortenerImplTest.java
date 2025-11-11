package com.pshakhlovich.coding.urlshortener.impl;

import com.pshakhlovich.coding.urlshortener.UrlShortener;
import org.junit.jupiter.api.Test;

import static com.pshakhlovich.coding.urlshortener.impl.UrlShortenerImpl.MAX_SHORTENED_URL_LENGTH;
import static com.pshakhlovich.coding.urlshortener.impl.UrlShortenerImpl.OUTPUT_DOMAIN;
import static org.assertj.core.api.Assertions.assertThat;

class UrlShortenerImplTest {

    private static final String INPUT_URL = "https://revolut.accounts/12345678";

    private final UrlShortener urlShortener = new UrlShortenerImpl();

    @Test
    void shortUrl_shouldShortenUrl_upToMax() {
        assertThat(urlShortener.shortUrl(INPUT_URL).length())
            .isLessThanOrEqualTo(MAX_SHORTENED_URL_LENGTH);
    }

    @Test
    void shortUrl_shouldContainDomain() {
        assertThat(urlShortener.shortUrl(INPUT_URL)).contains(OUTPUT_DOMAIN);
    }

    @Test
    void getOriginalUrl_shouldReturnUrlByShortenOne() {
        // given
        var shortenUrl = urlShortener.shortUrl(INPUT_URL);

        // then
        assertThat(urlShortener.getOriginalUrl(shortenUrl)).isEqualTo(INPUT_URL);
    }



}