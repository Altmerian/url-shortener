package com.pshakhlovich.coding.urlshortener.impl;

import com.pshakhlovich.coding.urlshortener.UrlShortener;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * URL shortener implementation using deterministic hash-based approach.
 *
 * <p>Features:
 * <ul>
 *   <li>Deterministic: Same input always produces same output</li>
 *   <li>Thread-safe: Handles concurrent requests safely</li>
 *   <li>Collision-aware: Detects and handles hash collisions</li>
 *   <li>Bidirectional: Efficient lookups in both directions</li>
 * </ul>
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Hash original URL using SHA-256</li>
 *   <li>Encode first 48 bits to Base62</li>
 *   <li>Produce 8-character alphanumeric short code</li>
 * </ol>
 */
public class HashBasedUrlShortener implements UrlShortener {

    // Configuration constants
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String DEFAULT_URL_SCHEME = "https";
    private static final String BASE_URL = "short.io";
    private static final int MAX_URL_LENGTH = 2048;
    private static final int SHORT_URL_LENGTH = 8;

    // Base62 alphabet: 0-9, A-Z, a-z (URL-safe, no special characters)
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final Logger logger = Logger.getLogger(HashBasedUrlShortener.class.getName());

    private static final ThreadLocal<MessageDigest> DIGEST_THREAD_LOCAL =
        ThreadLocal.withInitial(() -> {
            try {
                return MessageDigest.getInstance(HASH_ALGORITHM);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        });

    // Bidirectional mappings for O(1) lookups
    private final ConcurrentHashMap<String, String> shortCodeToOriginalUrl = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> originalUrlToShortCode = new ConcurrentHashMap<>();

    // Lock for atomic check-and-put operations
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Configuration
    private final UrlShortenerConfig config;

    /**
     * Creates a new URL shortener with default configuration.
     */
    public HashBasedUrlShortener() {
        this(UrlShortenerConfig.defaultConfig());
    }

    /**
     * Creates a new URL shortener with custom configuration.
     *
     * @param config the configuration to use
     */
    public HashBasedUrlShortener(UrlShortenerConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public String shortenUrl(String originalUrl) {
        // 1. Validate input
        validateUrl(originalUrl);

        // 2. Normalize URL (remove trailing slashes, convert to lowercase)
        String normalizedUrl = normalizeUrl(originalUrl);

        // 3. Check if already shortened (read lock)
        lock.readLock().lock();
        try {
            String existingShortCode = originalUrlToShortCode.get(normalizedUrl);
            if (existingShortCode != null) {
                logger.fine(() -> "URL already shortened: " + normalizedUrl + " -> " + existingShortCode);
                return buildShortUrl(existingShortCode);
            }
        } finally {
            lock.readLock().unlock();
        }

        // 4. Generate short code (write lock)
        lock.writeLock().lock();
        try {
            // Double-check in case another thread just created it
            String existingShortCode = originalUrlToShortCode.get(normalizedUrl);
            if (existingShortCode != null) {
                return buildShortUrl(existingShortCode);
            }

            // Generate deterministic short code from hash
            String shortCode = generateShortCode(normalizedUrl);

            // 5. Handle collision (extremely rare but possible)
            if (shortCodeToOriginalUrl.containsKey(shortCode)) {
                String existingUrl = shortCodeToOriginalUrl.get(shortCode);
                if (!existingUrl.equals(normalizedUrl)) {
                    // True collision - different URLs with same hash prefix
                    logger.warning("Hash collision detected for: " + normalizedUrl);
                    shortCode = handleCollision(normalizedUrl, shortCode);
                }
            }

            // 6. Store bidirectional mapping
            shortCodeToOriginalUrl.put(shortCode, normalizedUrl);
            originalUrlToShortCode.put(normalizedUrl, shortCode);

            String finalShortCode = shortCode;
            logger.info(() -> "Shortened: " + normalizedUrl + " -> " + finalShortCode);

            return buildShortUrl(shortCode);

        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String getOriginalUrl(String shortUrl) {
        // 1. Validate input
        if (shortUrl == null || shortUrl.isBlank()) {
            throw new IllegalArgumentException("Short URL must not be null or blank");
        }

        // 2. Extract short code from full URL
        String shortCode = extractShortCode(shortUrl);

        // 3. Lookup original URL
        lock.readLock().lock();
        try {
            return shortCodeToOriginalUrl.get(shortCode);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the short code for an original URL if it exists.
     *
     * @param originalUrl the original URL
     * @return Optional containing short code if exists, empty otherwise
     */
    public Optional<String> getShortCode(String originalUrl) {
        validateUrl(originalUrl);
        String normalizedUrl = normalizeUrl(originalUrl);

        lock.readLock().lock();
        try {
            return Optional.ofNullable(originalUrlToShortCode.get(normalizedUrl));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the number of URLs currently stored.
     *
     * @return the count of shortened URLs
     */
    public int size() {
        lock.readLock().lock();
        try {
            return shortCodeToOriginalUrl.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Validates that the URL is properly formatted.
     *
     * @param url the URL to validate
     * @throws IllegalArgumentException if URL is invalid
     */
    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL must not be null or blank");
        }

        if (url.length() > config.getMaxUrlLength()) {
            throw new IllegalArgumentException(
                "URL exceeds maximum length of " + config.getMaxUrlLength());
        }

        try {
            URI uri = URI.create(url);
            // Ensure the URI has a scheme (http, https, etc.)
            if (uri.getScheme() == null) {
                throw new IllegalArgumentException("Invalid URL format: missing scheme");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL format: " + url, e);
        }
    }

    /**
     * Normalizes URL for consistent hashing.
     * - Removes trailing slashes
     * - Converts to lowercase
     *
     * @param url the URL to normalize
     * @return normalized URL
     */
    private String normalizeUrl(String url) {
        String normalized = url.trim();

        // Remove trailing slash
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    /**
     * Generates an 8-character alphanumeric short code from URL.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Compute SHA-256 hash of URL</li>
     *   <li>Take first 48 bits (6 bytes)</li>
     *   <li>Convert to Base62 (alphanumeric)</li>
     *   <li>Pad/truncate to 8 characters</li>
     * </ol>
     *
     * @param url the URL to hash
     * @return 8-character alphanumeric short code
     */
    private String generateShortCode(String url) {
        var digest = DIGEST_THREAD_LOCAL.get();
        digest.reset();
        byte[] hashBytes = digest.digest(url.getBytes(StandardCharsets.UTF_8));

        // Take first 6 bytes (48 bits) and convert to positive long
        long hashValue = 0;
        for (int i = 0; i < 6; i++) {
            hashValue = (hashValue << 8) | (hashBytes[i] & 0xFF);
        }

        // Convert to Base62
        return toBase62(hashValue, config.getShortCodeLength());
    }

    /**
     * Converts a number to Base62 representation.
     *
     * @param number the number to convert
     * @param length the desired length (will be padded with zeros)
     * @return Base62 string
     */
    private String toBase62(long number, int length) {
        StringBuilder sb = new StringBuilder();

        // Handle negative numbers (make positive)
        long value = number & Long.MAX_VALUE;

        // Convert to base62
        while (value > 0) {
            int remainder = (int) (value % BASE62.length());
            sb.append(BASE62.charAt(remainder));
            value /= BASE62.length();
        }

        // Pad with zeros if needed
        while (sb.length() < length) {
            sb.append('0');
        }

        // Truncate if too long
        if (sb.length() > length) {
            sb.setLength(length);
        }

        return sb.reverse().toString();
    }

    /**
     * Handles hash collision by appending counter to URL before hashing.
     *
     * @param url           the original URL
     * @param collisionCode the code that collided
     * @return new unique short code
     */
    private String handleCollision(String url, String collisionCode) {
        int counter = 1;
        String newCode;

        do {
            // Append counter to URL and rehash
            String modifiedUrl = url + "#" + counter;
            newCode = generateShortCode(modifiedUrl);
            counter++;

            if (counter > 1000) {
                throw new IllegalStateException(
                    "Unable to resolve collision after 1000 attempts");
            }
        } while (shortCodeToOriginalUrl.containsKey(newCode));

        return newCode;
    }

    /**
     * Builds the complete short URL from a short code.
     *
     * @param shortCode the short code
     * @return complete short URL
     */
    private String buildShortUrl(String shortCode) {
        return String.format("%s://%s/%s",
            config.getScheme(),
            config.getDomain(),
            shortCode);
    }

    /**
     * Extracts short code from a full short URL.
     * Handles both full URLs and bare short codes.
     *
     * @param shortUrl the short URL or code
     * @return extracted short code
     */
    private String extractShortCode(String shortUrl) {
        String trimmed = shortUrl.trim();

        // If it's just the code (alphanumeric, matching configured length)
        if (trimmed.length() == config.getShortCodeLength() && isAlphanumeric(trimmed)) {
            return trimmed;
        }

        // Extract from full URL
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash != -1 && lastSlash < trimmed.length() - 1) {
            return trimmed.substring(lastSlash + 1);
        }

        throw new IllegalArgumentException("Invalid short URL format: " + shortUrl);
    }

    /**
     * Checks if string contains only alphanumeric characters.
     *
     * @param str the string to check
     * @return true if alphanumeric only
     */
    private boolean isAlphanumeric(String str) {
        return str.chars().allMatch(c ->
            (c >= '0' && c <= '9') ||
                (c >= 'A' && c <= 'Z') ||
                (c >= 'a' && c <= 'z'));
    }

    // ==================== Configuration Class ====================

    /**
     * Configuration for URL shortener.
     */
    public static class UrlShortenerConfig {
        private final String scheme;
        private final String domain;
        private final int maxUrlLength;
        private final int shortCodeLength;

        private UrlShortenerConfig(Builder builder) {
            this.scheme = builder.scheme;
            this.domain = builder.domain;
            this.maxUrlLength = builder.maxUrlLength;
            this.shortCodeLength = builder.shortCodeLength;
        }

        public static UrlShortenerConfig defaultConfig() {
            return builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getScheme() {
            return scheme;
        }

        public String getDomain() {
            return domain;
        }

        public int getMaxUrlLength() {
            return maxUrlLength;
        }

        public int getShortCodeLength() {
            return shortCodeLength;
        }

        public static class Builder {
            private String scheme = DEFAULT_URL_SCHEME;
            private String domain = BASE_URL;
            private int maxUrlLength = MAX_URL_LENGTH;
            private int shortCodeLength = SHORT_URL_LENGTH;

            public Builder scheme(String scheme) {
                this.scheme = scheme;
                return this;
            }

            public Builder domain(String domain) {
                this.domain = domain;
                return this;
            }

            public Builder maxUrlLength(int maxUrlLength) {
                this.maxUrlLength = maxUrlLength;
                return this;
            }

            public Builder shortCodeLength(int shortCodeLength) {
                this.shortCodeLength = shortCodeLength;
                return this;
            }

            public UrlShortenerConfig build() {
                Objects.requireNonNull(scheme, "scheme must not be null");
                Objects.requireNonNull(domain, "domain must not be null");
                if (maxUrlLength <= 0) {
                    throw new IllegalArgumentException("maxUrlLength must be positive");
                }
                if (shortCodeLength <= 0) {
                    throw new IllegalArgumentException("shortCodeLength must be positive");
                }
                return new UrlShortenerConfig(this);
            }
        }
    }
}
