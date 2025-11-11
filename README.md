# Coding Practice Repository

This is a Gradle multi-module project for coding practice and algorithm implementations.

## Project Structure

This repository contains the following modules:

### 1. URL Shortener (`urlShortener`)

A simple URL shortening service implementation with the following features:

- **Hash-based approach**: Uses SHA-256 for deterministic URL shortening
- **Base62 encoding**: Generates URL-safe alphanumeric short codes
- **Thread-safe**: Implements proper concurrency control with ReadWriteLock
- **Collision handling**: Automatic collision resolution with rehashing
- **Bidirectional mapping**: O(1) lookups for both short codes and original URLs
- **Configurable**: Customizable domain, scheme, URL length limits, and short code length
- **URL normalization**: Consistent handling of URLs with/without trailing slashes
- **Input validation**: Comprehensive validation for URL format and length

**Main classes:**
- `UrlShortener` - Interface defining the contract
- `HashBasedUrlShortener` - Main implementation with configuration support

**Usage example:**
```java
// Default configuration
var shortener = new HashBasedUrlShortener();
String shortUrl = shortener.shortenUrl("https://example.com/very/long/url");
String originalUrl = shortener.getOriginalUrl(shortUrl);

// Custom configuration
var config = HashBasedUrlShortener.UrlShortenerConfig.builder()
    .domain("my.short")
    .scheme("https")
    .shortCodeLength(6)
    .maxUrlLength(4096)
    .build();
var customShortener = new HashBasedUrlShortener(config);
```

### 2. Data Structures & Algorithms (`dsa`)

A module for practicing and implementing various data structures and algorithms.

## Building the Project

This project uses Gradle as the build tool. You can build all modules using:

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :urlShortener:build
./gradlew :dsa:build
```

## Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :urlShortener:test
./gradlew :dsa:test
```

## Requirements

- Java 21 or higher
- Gradle 8.5 (wrapper included)

## Project Configuration

The project uses:
- JUnit 5 for testing
- AssertJ for fluent assertions
- Java 21 language level

## Adding New Modules

To add a new module, create a new `Gradle` module under the root and add it to `settings.gradle`.



