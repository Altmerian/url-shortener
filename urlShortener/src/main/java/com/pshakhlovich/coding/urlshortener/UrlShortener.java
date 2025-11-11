package com.pshakhlovich.coding.urlshortener;

public interface UrlShortener {

    String shortUrl(String originalUrl);

    String getOriginalUrl(String shortenUrl);
}
