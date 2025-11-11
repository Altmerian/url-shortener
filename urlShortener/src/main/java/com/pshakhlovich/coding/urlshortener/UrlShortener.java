package com.pshakhlovich.coding.urlshortener;

public interface UrlShortener {

    String shortenUrl(String originalUrl);

    String getOriginalUrl(String shortenUrl);
}
