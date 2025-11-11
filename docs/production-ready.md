# URL Shortener Deep Dive: Complete Technical Explanation

## Table of Contents
1. [Core Algorithm Overview](#1-core-algorithm-overview)
2. [SHA-256 Hashing Explained](#2-sha-256-hashing-explained)
3. [Base62 Encoding Deep Dive](#3-base62-encoding-deep-dive)
4. [Thread Safety Implementation](#4-thread-safety-implementation)
5. [Collision Detection & Handling](#5-collision-detection--handling)
6. [URL Normalization](#6-url-normalization)
7. [Bidirectional Mapping Strategy](#7-bidirectional-mapping-strategy)
8. [Configuration Design Pattern](#8-configuration-design-pattern)
9. [Performance Analysis](#9-performance-analysis)
10. [Production Scaling Strategies](#10-production-scaling-strategies)

---

## 1. Core Algorithm Overview

### The Big Picture

```
┌─────────────┐
│ Original URL│ 
│ https://... │
└──────┬──────┘
       │
       ▼
┌──────────────────┐
│ 1. Normalize URL │  Remove trailing slashes, trim
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ 2. Check Cache   │  Already shortened? Return existing
└──────┬───────────┘
       │ (cache miss)
       ▼
┌──────────────────┐
│ 3. SHA-256 Hash  │  Compute 256-bit hash
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ 4. Extract Bits  │  Take first 48 bits
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ 5. Base62 Encode │  Convert to alphanumeric
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ 6. Check Coll.   │  Rare: handle if exists
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│ 7. Store Both    │  shortCode→URL & URL→shortCode
└──────┬───────────┘
       │
       ▼
┌─────────────┐
│  Short URL  │
│ https://s/  │
│  abc12xyz   │
└─────────────┘
```

### Why This Approach?

**Requirements:**
- ✅ Deterministic (same input → same output)
- ✅ 8 characters alphanumeric only
- ✅ Thread-safe
- ✅ Fast lookup (O(1))
- ✅ Handle collisions

**Alternatives Considered:**

| Approach | Pros | Cons |
|----------|------|------|
| **Random generation** | Simple, fast | Not deterministic, needs collision checking |
| **Counter (auto-increment)** | Sequential, predictable length | Not deterministic, needs distributed coordination |
| **UUID** | Guaranteed unique | Too long (36 chars), not deterministic |
| **Hash-based** ✅ | Deterministic, fixed length, fast | Potential collisions (rare) |

**Winner:** Hash-based approach with collision handling.

---

## 2. SHA-256 Hashing Explained

### What is SHA-256?

**SHA-256** (Secure Hash Algorithm 256-bit) is a cryptographic hash function that:
1. Takes input of any size
2. Produces a **fixed 256-bit (32-byte)** output
3. Is **deterministic** - same input always produces same output
4. Is **one-way** - cannot reverse the hash to get original input
5. Has **avalanche effect** - tiny input change completely changes output

### How It Works (Simplified)

```java
// Input: Any string
String url = "https://example.com/page";

// Process: SHA-256 algorithm
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hashBytes = digest.digest(url.getBytes(StandardCharsets.UTF_8));

// Output: 32 bytes (256 bits)
// Example: [0x6b, 0xc1, 0xbe, 0xe2, 0x2e, 0x40, 0x9f, 0x96, ...]
```

### Visual Example

```
Input:  "https://example.com/page1"
        ↓
SHA-256 Algorithm (complex math operations)
        ↓
Output: 6bc1bee22e409f96cdf05e15bd9b73e06bbb6f9f8c2e2e7f8d9a0b1c2d3e4f5a
        (32 bytes in hexadecimal)
```

```
Input:  "https://example.com/page2"  ← Just changed '1' to '2'
        ↓
SHA-256 Algorithm
        ↓
Output: a7b2c4d8e1f3a5b7c9d2e4f6a8b0c2d4e6f8a0b2c4d6e8f0a2b4c6d8e0f2a4b6
        (Completely different!)
```

### Why SHA-256 for URL Shortening?

```java
// Property 1: Deterministic
String url = "https://example.com";
String hash1 = sha256(url);  // abc123...
String hash2 = sha256(url);  // abc123... (same!)

// Property 2: Uniform distribution
String url1 = "https://example.com/page1";  // hash: 6bc1be...
String url2 = "https://example.com/page2";  // hash: a7b2c4...
// Very different hashes, reduces collision probability

// Property 3: Fast computation
// SHA-256 is optimized and hardware-accelerated on modern CPUs
```

### Taking First 48 Bits

**Why 48 bits?**

```
Full SHA-256 output: 256 bits (too long)
We need: 8 characters in Base62

Base62 has 62 symbols: [0-9, A-Z, a-z]
How many unique combinations?
  62^8 = 218,340,105,584,896 ≈ 218 trillion
  
In binary: log2(62^8) ≈ 47.6 bits

So we need ~48 bits to represent 8 Base62 characters
```

**Code Breakdown:**

```java
byte[] hashBytes = messageDigest.digest(url.getBytes(StandardCharsets.UTF_8));
// hashBytes = [byte0, byte1, byte2, ..., byte31]  (32 bytes)

// Take first 6 bytes (48 bits)
long hashValue = 0;
for (int i = 0; i < 6; i++) {
    hashValue = (hashValue << 8) | (hashBytes[i] & 0xFF);
}
```

**Step-by-step example:**

```
hashBytes = [0x6b, 0xc1, 0xbe, 0xe2, 0x2e, 0x40, ...]

Iteration 0:
  hashValue = 0
  hashValue = (0 << 8) | (0x6b & 0xFF) = 0x6b

Iteration 1:
  hashValue = 0x6b
  hashValue = (0x6b << 8) | (0xc1 & 0xFF) = 0x6bc1

Iteration 2:
  hashValue = 0x6bc1
  hashValue = (0x6bc1 << 8) | (0xbe & 0xFF) = 0x6bc1be

... and so on for 6 bytes total

Final: hashValue = 0x6bc1bee22e40 (48 bits)
```

**Why `& 0xFF`?**

```java
// In Java, byte is SIGNED (-128 to 127)
byte b = (byte) 0xFF;  // This is -1 in Java!

// When promoting byte to int, Java sign-extends:
int i = b;  // i = 0xFFFFFFFF (all 1s) - WRONG!

// We want only the byte value:
int i = b & 0xFF;  // i = 0x000000FF - CORRECT!
```

### Collision Probability

With 48 bits, what's the collision probability?

```
Total possible hashes: 2^48 = 281,474,976,710,656 (281 trillion)

Using Birthday Paradox:
  - With 1 million URLs: collision probability ≈ 0.0000018% 
  - With 1 billion URLs: collision probability ≈ 0.18%

This is acceptably low! And we have collision handling as backup.
```

---

## 3. Base62 Encoding Deep Dive

### What is Base62?

Base62 is a numeral system using 62 symbols: `0-9A-Za-z`

```
Base62 alphabet:
0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz
^         ^^                         ^^                         ^
|         ||                         ||                         |
0         9|                         ||                         61
          10                         35                          
```

**Why Base62?**
- ✅ **URL-safe**: No special characters that need escaping
- ✅ **Case-sensitive**: More combinations than Base36 (0-9, a-z)
- ✅ **Human-readable**: Looks cleaner than Base64 (no +, /, =)
- ✅ **Compact**: More efficient than Base10 or Base16

### Base Conversion Fundamentals

**Decimal to Base62 Conversion (Like Converting to Binary or Hex):**

```
Decimal to Binary (Base2):
  13 in decimal = 1101 in binary
  13 ÷ 2 = 6 remainder 1  ← least significant bit
   6 ÷ 2 = 3 remainder 0
   3 ÷ 2 = 1 remainder 1
   1 ÷ 2 = 0 remainder 1  ← most significant bit
  Read bottom to top: 1101

Same principle for Base62:
  123456 in decimal = Base62
  123456 ÷ 62 = 1991 remainder 14 → 'E'
    1991 ÷ 62 =   32 remainder  7 → '7'
      32 ÷ 62 =    0 remainder 32 → 'W'
  Read bottom to top: "W7E"
```

### Code Walkthrough

```java
private String toBase62(long number, int length) {
    StringBuilder sb = new StringBuilder();
    
    // Handle negative numbers (make positive)
    long value = number & Long.MAX_VALUE;
    
    // Convert to base62
    while (value > 0) {
        int remainder = (int) (value % BASE62_BASE);  // BASE62_BASE = 62
        sb.append(BASE62_ALPHABET.charAt(remainder));
        value /= BASE62_BASE;
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
```

### Detailed Example

Let's convert `hashValue = 123456789012345` to Base62:

```
Step 1: value = 123456789012345

Iteration 1:
  remainder = 123456789012345 % 62 = 9
  sb = "9"
  value = 123456789012345 / 62 = 1991238855039

Iteration 2:
  remainder = 1991238855039 % 62 = 39 → 'd' (alphabet[39])
  sb = "9d"
  value = 1991238855039 / 62 = 32116594274

Iteration 3:
  remainder = 32116594274 % 62 = 50 → 'o' (alphabet[50])
  sb = "9do"
  value = 32116594274 / 62 = 518009585

... (continue until value = 0)

Final sb: "9doX3hZ" (reversed from building)
Reverse: "Zh3Xod9"

If length = 8:
  Need padding: "Zh3Xod9" → "0Zh3Xod9" (pad with '0')
```

### Why Reverse?

```
Building process (least to most significant):
  [9] → [9, d] → [9, d, o] → ... → [9, d, o, X, 3, h, Z]
  
But we read numbers left-to-right (most to least significant):
  We want: Zh3Xod9
  Not: 9doX3hZ
  
So we reverse at the end!
```

### Base62 vs Other Encodings

```java
long number = 123456789012345L;

Base10: "123456789012345"     (15 chars)
Base16: "704885F926B9"         (12 chars) 
Base36: "1DNU04IRD"            (9 chars)
Base62: "0Zh3Xod9"             (8 chars) ✅
Base64: "Ah+/Yds="             (8 chars, but has special chars)
```

**Base62 achieves the sweet spot: compact + URL-safe!**

---

## 4. Thread Safety Implementation

### The Problem

```java
// Thread 1                          // Thread 2
if (!map.containsKey(url)) {         if (!map.containsKey(url)) {
    String code = generate();            String code = generate();
    map.put(url, code);                  map.put(url, code);
}                                    }

// Race condition! Both might create different codes for same URL
```

### Understanding ReadWriteLock

**Concept:**
- Multiple threads can **read simultaneously** (concurrent reads)
- Only **one thread can write** (exclusive write)
- Writers block readers and other writers

```
┌──────────────────────────────────┐
│         ReadWriteLock            │
├──────────────────────────────────┤
│                                  │
│  Read Lock        Write Lock     │
│  ─────────────    ───────────    │
│  • Multiple OK    • Exclusive    │
│  • Concurrent     • Blocks all   │
│  • No blocking    • No sharing   │
│    other reads                   │
└──────────────────────────────────┘
```

### Implementation Pattern

```java
private final ReadWriteLock lock = new ReentrantReadWriteLock();

public String shortUrl(String originalUrl) {
    // Step 1: Fast check with read lock (allows concurrency)
    lock.readLock().lock();
    try {
        String existing = originalUrlToShortCode.get(originalUrl);
        if (existing != null) {
            return buildShortUrl(existing);  // Cache hit!
        }
    } finally {
        lock.readLock().unlock();  // ALWAYS unlock in finally
    }
    
    // Step 2: Create new entry with write lock (exclusive)
    lock.writeLock().lock();
    try {
        // DOUBLE-CHECK! Another thread might have created it
        String existing = originalUrlToShortCode.get(originalUrl);
        if (existing != null) {
            return buildShortUrl(existing);
        }
        
        // Generate and store
        String shortCode = generateShortCode(normalizedUrl);
        shortCodeToOriginalUrl.put(shortCode, normalizedUrl);
        originalUrlToShortCode.put(normalizedUrl, shortCode);
        
        return buildShortUrl(shortCode);
    } finally {
        lock.writeLock().unlock();
    }
}
```

### Why Double-Check Locking?

```
Timeline with 3 threads:

T=0: Thread A: read lock → miss → release read lock
T=1: Thread B: read lock → miss → release read lock
T=2: Thread C: read lock → miss → release read lock

T=3: Thread A: acquire write lock
     Thread B: waiting for write lock
     Thread C: waiting for write lock

T=4: Thread A: double-check → not found → generate → store → release

T=5: Thread B: acquire write lock
     Thread B: double-check → FOUND! (Thread A created it)
     Thread B: return existing → release
     Thread C: still waiting

T=6: Thread C: acquire write lock
     Thread C: double-check → FOUND! (Thread A created it)
     Thread C: return existing → release
```

**Without double-check:** Threads B and C would wastefully regenerate!

### Read-Write Lock Benefits

```java
// Scenario: 95% reads, 5% writes

Without ReadWriteLock (using synchronized):
┌─────────────────────────────────────┐
│ Thread 1: Read  (blocked by Thread 2)│
│ Thread 2: Write (exclusive)          │
│ Thread 3: Read  (blocked)            │
│ Thread 4: Read  (blocked)            │
└─────────────────────────────────────┘
All serialized! Slow!

With ReadWriteLock:
┌─────────────────────────────────────┐
│ Thread 1: Read  ─┐                  │
│ Thread 3: Read  ─┤ Concurrent!      │
│ Thread 4: Read  ─┘                  │
│ Thread 2: Write (waits, then excl.) │
└─────────────────────────────────────┘
Reads don't block each other! Fast!
```

### Thread Safety of MessageDigest

```java
private final MessageDigest messageDigest;

private String generateShortCode(String url) {
    byte[] hashBytes;
    
    // CRITICAL: MessageDigest is NOT thread-safe!
    synchronized (messageDigest) {
        messageDigest.reset();  // Clear previous state
        hashBytes = messageDigest.digest(url.getBytes(StandardCharsets.UTF_8));
    }
    
    // Rest of processing...
}
```

**Why synchronize MessageDigest?**

```java
// Without synchronization:
// Thread 1                          Thread 2
digest.reset();                      digest.reset();  // ← Interferes!
digest.update(bytes1);               digest.update(bytes2);  
hash1 = digest.digest();             hash2 = digest.digest();
// Both get corrupted results!

// With synchronization:
synchronized(digest) {
    digest.reset();
    digest.update(bytes);
    hash = digest.digest();
}  // Only one thread at a time
```

### Alternative: ThreadLocal

```java
// Alternative approach: One MessageDigest per thread
private static final ThreadLocal<MessageDigest> DIGEST_THREAD_LOCAL =
    ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    });

private String generateShortCode(String url) {
    MessageDigest digest = DIGEST_THREAD_LOCAL.get();
    digest.reset();
    byte[] hashBytes = digest.digest(url.getBytes(StandardCharsets.UTF_8));
    // No synchronization needed!
}
```

**Trade-offs:**

| Approach | Pros | Cons |
|----------|------|------|
| **Synchronized** | Memory efficient | Contention on writes |
| **ThreadLocal** | No contention | More memory (one per thread) |

For URL shortener, **synchronized is better** - writes are infrequent.

---

## 5. Collision Detection & Handling

### What is a Collision?

```
URL 1: "https://example.com/pageA"
       ↓ SHA-256 ↓
       Hash: 0x6bc1bee2...
       ↓ Take 48 bits ↓
       0x6bc1bee22e40
       ↓ Base62 ↓
       "abc12xyz"

URL 2: "https://different.com/totally-unrelated"
       ↓ SHA-256 ↓
       Hash: 0x6bc1be22...  ← Different full hash
       ↓ Take 48 bits ↓
       0x6bc1bee22e40  ← SAME first 48 bits! (collision)
       ↓ Base62 ↓
       "abc12xyz"
```

### Collision Probability Math

**Birthday Paradox Applied:**

```
Problem: With N possible hash values, how many URLs until 
         50% chance of collision?

Answer: √(π/2 × N) ≈ 1.25 × √N

For 48-bit hashes:
  N = 2^48 = 281 trillion
  50% collision at: 1.25 × √(281 trillion) 
                  ≈ 21 million URLs

For practical usage:
  - At 1M URLs: 0.0018% collision chance (1.8 in 100,000)
  - At 10M URLs: 0.18% collision chance
  - At 100M URLs: 18% collision chance
```

### Detection Strategy

```java
// After generating short code
if (shortCodeToOriginalUrl.containsKey(shortCode)) {
    String existingUrl = shortCodeToOriginalUrl.get(shortCode);
    
    if (!existingUrl.equals(normalizedUrl)) {
        // TRUE COLLISION: Different URLs, same code
        logger.warning("Hash collision detected!");
        shortCode = handleCollision(normalizedUrl, shortCode);
    }
    // else: Same URL, already shortened (not a collision)
}
```

### Collision Handling Algorithm

```java
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
```

**How it works:**

```
Original collision:
  URL: "https://example.com/pageA"
  Hash: abc12xyz (collision!)

Attempt 1:
  Modified URL: "https://example.com/pageA#1"
  New hash: def45uvw
  Check: Still exists? Try again.

Attempt 2:
  Modified URL: "https://example.com/pageA#2"
  New hash: ghi78rst
  Check: Available! Use this.

Store both:
  ghi78rst → "https://example.com/pageA" (original, not modified)
```

**Key insight:** We hash the modified URL but store the original!

### Alternative Collision Strategies

#### Strategy 1: Incremental Suffix

```java
// Instead of "#1", try next available slot
String baseCode = "abc12xyz";
for (int i = 0; i < 62; i++) {
    String candidate = baseCode.substring(0, 7) + BASE62_ALPHABET.charAt(i);
    if (!used(candidate)) return candidate;
}
```

**Pros:** Predictable, sequential  
**Cons:** Need to track which suffixes are used

#### Strategy 2: Use More Bits

```java
// Instead of 48 bits, use 54 bits (9 characters)
// Lower collision rate, but longer URLs
private static final int SHORT_CODE_LENGTH = 9;
```

**Pros:** Fewer collisions  
**Cons:** Longer URLs (violates 8-char requirement)

#### Strategy 3: Salting

```java
// Add timestamp salt
String salted = url + System.currentTimeMillis();
String hash = sha256(salted);
```

**Pros:** Virtually no collisions  
**Cons:** Not deterministic! (violates requirement)

**Winner:** Rehashing with counter (deterministic + handles collisions)

---

## 6. URL Normalization

### Why Normalize?

```java
// These should be considered the SAME:
"https://example.com/page"
"https://example.com/page/"      ← trailing slash
"https://example.com/page "      ← trailing space
"HTTPS://EXAMPLE.COM/page"       ← different case (domain)

// Without normalization:
hash("https://example.com/page")  = "abc12xyz"
hash("https://example.com/page/") = "def45uvw"  ← Different!

// User confusion: "Why do I get different short URLs?"
```

### Normalization Rules

```java
private String normalizeUrl(String url) {
    String normalized = url.trim();  // Remove whitespace
    
    // Remove trailing slash
    if (normalized.endsWith("/")) {
        normalized = normalized.substring(0, normalized.length() - 1);
    }
    
    return normalized;
}
```

### Advanced Normalization (Production)

```java
private String normalizeUrl(String url) {
    try {
        URL parsed = new URL(url);
        
        // 1. Lowercase scheme and domain
        String scheme = parsed.getProtocol().toLowerCase();
        String host = parsed.getHost().toLowerCase();
        
        // 2. Remove default ports
        int port = parsed.getPort();
        String portStr = "";
        if (port != -1 && !isDefaultPort(scheme, port)) {
            portStr = ":" + port;
        }
        
        // 3. Normalize path
        String path = parsed.getPath();
        if (path.isEmpty()) {
            path = "/";
        }
        // Remove trailing slash (except root)
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        
        // 4. Sort query parameters (optional, for consistency)
        String query = parsed.getQuery();
        if (query != null) {
            query = sortQueryParameters(query);
        }
        
        // 5. Keep fragment if present
        String fragment = parsed.getRef();
        
        // Reconstruct
        StringBuilder normalized = new StringBuilder();
        normalized.append(scheme).append("://").append(host).append(portStr);
        normalized.append(path);
        if (query != null) {
            normalized.append("?").append(query);
        }
        if (fragment != null) {
            normalized.append("#").append(fragment);
        }
        
        return normalized.toString();
        
    } catch (MalformedURLException e) {
        // Fall back to simple normalization
        return url.trim().replaceAll("/+$", "");
    }
}

private boolean isDefaultPort(String scheme, int port) {
    return (scheme.equals("http") && port == 80) ||
           (scheme.equals("https") && port == 443);
}

private String sortQueryParameters(String query) {
    // Split by '&', sort, rejoin
    String[] params = query.split("&");
    Arrays.sort(params);
    return String.join("&", params);
}
```

### Normalization Examples

```
Input:  "HTTPS://Example.COM:443/Page/?b=2&a=1#section"
Output: "https://example.com/page?a=1&b=2#section"

Changes:
✓ Lowercased scheme: HTTPS → https
✓ Lowercased domain: Example.COM → example.com
✓ Removed default port: :443 → (removed)
✓ Lowercased path: /Page/ → /page
✓ Sorted query: b=2&a=1 → a=1&b=2
✓ Kept fragment: #section
```

### Edge Cases

```java
// Edge case 1: Root path
"https://example.com" → "https://example.com/"  (add slash)
"https://example.com/" → "https://example.com/" (keep slash)

// Edge case 2: Query parameters order
"https://example.com?a=1&b=2" vs "https://example.com?b=2&a=1"
→ Should both normalize to "https://example.com?a=1&b=2"

// Edge case 3: Fragment identifiers
"https://example.com/page#section1" vs "https://example.com/page#section2"
→ Different! Fragments matter for distinct content

// Edge case 4: Unicode/Encoded characters
"https://example.com/café" vs "https://example.com/caf%C3%A9"
→ Should be normalized to same representation (prefer decoded)
```

---

## 7. Bidirectional Mapping Strategy

### Why Two Maps?

```java
// Bad: Only one map
Map<String, String> shortCodeToOriginalUrl;

// Getting short code from original URL requires ITERATION:
String getShortCode(String originalUrl) {
    for (Map.Entry<String, String> entry : map.entrySet()) {
        if (entry.getValue().equals(originalUrl)) {
            return entry.getKey();  // Found it!
        }
    }
    return null;  // O(n) - SLOW!
}

// Good: Two maps
Map<String, String> shortCodeToOriginalUrl;  // short → original
Map<String, String> originalUrlToShortCode;  // original → short

// Both directions are O(1):
String getOriginalUrl(String shortCode) {
    return shortCodeToOriginalUrl.get(shortCode);  // O(1)
}

String getShortCode(String originalUrl) {
    return originalUrlToShortCode.get(originalUrl);  // O(1)
}
```

### Memory Trade-off

```
Single map: 
  Memory: N entries
  Lookup: O(1) one direction, O(n) other direction

Bidirectional maps:
  Memory: 2N entries  (2x memory)
  Lookup: O(1) both directions

For URL shortener: Lookup speed > Memory
  - Reads are frequent (every click)
  - Memory is cheap
  - Speed is critical
```

### Consistency Guarantee

```java
lock.writeLock().lock();
try {
    // ATOMIC: Both maps updated together
    shortCodeToOriginalUrl.put(shortCode, normalizedUrl);
    originalUrlToShortCode.put(normalizedUrl, shortCode);
    // If exception occurs, neither is updated (transaction-like)
} finally {
    lock.writeLock().unlock();
}
```

### Why ConcurrentHashMap?

```java
// ConcurrentHashMap features:
// 1. Thread-safe operations (put, get, containsKey)
// 2. Lock-free reads (fast!)
// 3. Fine-grained locking for writes (segments)
// 4. No locking on iteration (weakly consistent)

private final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

// Individual operations are thread-safe:
map.put(key, value);     // Thread-safe
String v = map.get(key); // Thread-safe

// But compound operations are NOT:
if (!map.containsKey(key)) {  // Thread A checks
    map.put(key, value);      // Thread B might insert here!
}
// This is why we need ReadWriteLock for check-then-act!
```

### Alternative: Single Map with Custom Object

```java
// Alternative design: Single map with bidirectional object
class UrlMapping {
    String shortCode;
    String originalUrl;
}

Map<String, UrlMapping> index;  // Can index by either field

// But requires more complex indexing logic
// Two maps is simpler and clearer
```

---

## 8. Configuration Design Pattern

### Builder Pattern Explained

```java
// Problem: Constructor with many parameters
public UrlShortener(String scheme, String domain, int maxLength, 
                    boolean enableCache, int cacheSize, ...) {
    // 10+ parameters! Hard to read and use
}

// Creating instance is confusing:
UrlShortener shortener = new UrlShortener(
    "https", 
    "short.io", 
    2048, 
    true, 
    1000,
    ...  // What does each parameter mean?
);
```

**Builder Pattern Solution:**

```java
UrlShortener shortener = new UrlShortener(
    UrlShortenerConfig.builder()
        .scheme("https")
        .domain("short.io")
        .maxUrlLength(2048)
        .build()
);

// Clear, readable, self-documenting!
```

### Implementation Deep Dive

```java
public static class UrlShortenerConfig {
    // Immutable fields
    private final String scheme;
    private final String domain;
    private final int maxUrlLength;
    
    // Private constructor - forces use of builder
    private UrlShortenerConfig(Builder builder) {
        this.scheme = builder.scheme;
        this.domain = builder.domain;
        this.maxUrlLength = builder.maxUrlLength;
    }
    
    // Factory method for default config
    public static UrlShortenerConfig defaultConfig() {
        return builder().build();
    }
    
    // Entry point: get builder
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters only (no setters - immutable!)
    public String getScheme() { return scheme; }
    public String getDomain() { return domain; }
    public int getMaxUrlLength() { return maxUrlLength; }
    
    // Builder inner class
    public static class Builder {
        // Mutable fields with defaults
        private String scheme = "https";
        private String domain = "short.io";
        private int maxUrlLength = 2048;
        
        // Fluent setters (return this)
        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;  // Enables chaining
        }
        
        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }
        
        public Builder maxUrlLength(int maxUrlLength) {
            this.maxUrlLength = maxUrlLength;
            return this;
        }
        
        // Build method: validate and create
        public UrlShortenerConfig build() {
            // Validation
            Objects.requireNonNull(scheme, "scheme must not be null");
            Objects.requireNonNull(domain, "domain must not be null");
            if (maxUrlLength <= 0) {
                throw new IllegalArgumentException("maxUrlLength must be positive");
            }
            
            // Create immutable config
            return new UrlShortenerConfig(this);
        }
    }
}
```

### Why Immutable Config?

```java
// Mutable config (BAD):
Config config = new Config();
config.setDomain("short.io");

UrlShortener shortener1 = new UrlShortener(config);
UrlShortener shortener2 = new UrlShortener(config);

config.setDomain("other.io");  // ← Changes behavior of shortener1/2!

// Immutable config (GOOD):
UrlShortenerConfig config = UrlShortenerConfig.builder()
    .domain("short.io")
    .build();

UrlShortener shortener1 = new UrlShortener(config);
UrlShortener shortener2 = new UrlShortener(config);

// Cannot change config after creation!
// Each shortener has predictable, unchanging behavior
```

### Usage Examples

```java
// Example 1: Default configuration
UrlShortener shortener = new UrlShortener();

// Example 2: Custom domain
UrlShortener shortener = new UrlShortener(
    UrlShortenerConfig.builder()
        .domain("custom.short")
        .build()
);

// Example 3: Full customization
UrlShortener shortener = new UrlShortener(
    UrlShortenerConfig.builder()
        .scheme("http")
        .domain("localhost")
        .maxUrlLength(1024)
        .build()
);

// Example 4: Programmatic configuration
String domain = System.getenv("SHORT_DOMAIN");
UrlShortener shortener = new UrlShortener(
    UrlShortenerConfig.builder()
        .domain(domain != null ? domain : "default.short")
        .build()
);
```

---

## 9. Performance Analysis

### Time Complexity

| Operation | Average Case | Worst Case | Explanation |
|-----------|-------------|------------|-------------|
| `shortUrl()` (cache hit) | O(1) | O(1) | HashMap lookup |
| `shortUrl()` (cache miss) | O(1)* | O(k) | Hash + Base62 encoding |
| `getOriginalUrl()` | O(1) | O(1) | HashMap lookup |
| `getShortCode()` | O(1) | O(1) | HashMap lookup |

*O(1) amortized - SHA-256 computation is O(n) where n = URL length, but URLs are bounded (max 2048 chars), so treated as constant.

### Space Complexity

```
Per URL stored:
  - shortCodeToOriginalUrl entry: 
      8 bytes (short code) + ~100 bytes (avg URL) = ~108 bytes
  - originalUrlToShortCode entry: 
      ~100 bytes (URL) + 8 bytes (short code) = ~108 bytes
  - HashMap overhead: ~32 bytes per entry × 2 = ~64 bytes
  
  Total per URL: ~280 bytes

For 1 million URLs:
  280 bytes × 1M = 280 MB (manageable!)

For 1 billion URLs:
  280 bytes × 1B = 280 GB (needs distributed storage)
```

### Benchmarking

```java
public class UrlShortenerBenchmark {
    
    @Test
    void benchmarkShortening() {
        UrlShortener shortener = new ProductionUrlShortener();
        int iterations = 1_000_000;
        
        long start = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            String url = "https://example.com/page" + i;
            shortener.shortUrl(url);
        }
        
        long end = System.nanoTime();
        long durationMs = (end - start) / 1_000_000;
        
        System.out.println("Total time: " + durationMs + "ms");
        System.out.println("Average: " + (durationMs / (double) iterations) + "ms per URL");
        System.out.println("Throughput: " + (iterations / (durationMs / 1000.0)) + " URLs/sec");
    }
    
    @Test
    void benchmarkLookup() {
        UrlShortener shortener = new ProductionUrlShortener();
        
        // Pre-populate
        List<String> shortUrls = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            shortUrls.add(shortener.shortUrl("https://example.com/page" + i));
        }
        
        // Benchmark lookups
        long start = System.nanoTime();
        
        for (String shortUrl : shortUrls) {
            shortener.getOriginalUrl(shortUrl);
        }
        
        long end = System.nanoTime();
        long durationMs = (end - start) / 1_000_000;
        
        System.out.println("Lookup throughput: " + 
            (shortUrls.size() / (durationMs / 1000.0)) + " lookups/sec");
    }
}
```

**Expected Results:**

```
Shortening (first time):
  ~0.5-1ms per URL
  ~1,000-2,000 URLs/sec per thread

Shortening (cache hit):
  ~0.001ms per URL
  ~1,000,000 URLs/sec per thread

Lookup:
  ~0.0001ms per lookup
  ~10,000,000 lookups/sec per thread
```

### Bottlenecks

```java
// 1. SHA-256 computation (CPU-bound)
synchronized (messageDigest) {
    hashBytes = messageDigest.digest(...);  // ~0.1-0.5ms
}

// 2. String operations (memory allocation)
String normalized = url.trim().replaceAll(...);  // ~0.01ms

// 3. HashMap contention (multi-threaded)
lock.writeLock().lock();  // Wait time varies with contention
```

### Optimization Strategies

#### 1. Cache MessageDigest per Thread

```java
private static final ThreadLocal<MessageDigest> DIGEST =
    ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    });

// No synchronization needed!
byte[] hash = DIGEST.get().digest(bytes);
```

**Impact:** Eliminates synchronization contention on writes.

#### 2. String Interning for Common Domains

```java
// Many URLs share same domain
URL parsed = new URL(url);
String domain = parsed.getHost().intern();  // Reuse string instance

// Reduces memory by ~30% for URLs with common domains
```

#### 3. Bloom Filter for Existence Check

```java
// Before expensive hash computation, quick check
private final BloomFilter<String> existingUrls = 
    BloomFilter.create(Funnels.stringFunnel(UTF_8), 1_000_000, 0.01);

public String shortUrl(String url) {
    if (existingUrls.mightContain(url)) {
        // Probably exists, check map
        String existing = originalUrlToShortCode.get(url);
        if (existing != null) return buildShortUrl(existing);
    }
    
    // Definitely doesn't exist, generate new
    String shortCode = generateShortCode(url);
    existingUrls.put(url);
    // ...
}
```

**Impact:** Reduces hash computations by ~90% for repeat URLs.

---

# Production Scaling Strategies

## Scaling to one Billion URLs

## Challenge 1: Memory Limitations

```
280 bytes per URL × 1B URLs = 280 GB RAM (single machine)

Solutions:
1. Distributed caching (Redis Cluster)
2. Database persistence (PostgreSQL/Cassandra)
3. Hybrid: Hot data in cache, cold data in DB
```

### Strategy: Multi-Tier Architecture

```
┌─────────────┐
│  API Layer  │  (Stateless, horizontally scalable)
└──────┬──────┘
       │
┌──────▼──────┐
│ Cache Layer │  (Redis Cluster - hot data)
└──────┬──────┘
       │ (cache miss)
┌──────▼──────┐
│ DB Layer    │  (PostgreSQL + read replicas)
└─────────────┘
```

### Implementation Sketch

```java
public class DistributedUrlShortener implements UrlShortener {
    
    private final RedisClient redis;        // Hot cache
    private final Database database;        // Cold storage
    private final LocalCache localCache;    // L1 cache
    
    @Override
    public String shortUrl(String originalUrl) {
        // 1. Check local cache (L1)
        String cached = localCache.get(originalUrl);
        if (cached != null) return cached;
        
        // 2. Check Redis (L2)
        cached = redis.get("url:" + originalUrl);
        if (cached != null) {
            localCache.put(originalUrl, cached);
            return cached;
        }
        
        // 3. Check database (cold storage)
        cached = database.queryShortCode(originalUrl);
        if (cached != null) {
            redis.set("url:" + originalUrl, cached, EXPIRY);
            localCache.put(originalUrl, cached);
            return cached;
        }
        
        // 4. Generate new (with distributed lock)
        try (DistributedLock lock = redis.acquireLock("lock:" + originalUrl)) {
            // Double-check after acquiring lock
            cached = database.queryShortCode(originalUrl);
            if (cached != null) return cached;
            
            // Generate
            String shortCode = generateShortCode(originalUrl);
            
            // Persist
            database.insert(shortCode, originalUrl);
            redis.set("url:" + originalUrl, shortCode);
            localCache.put(originalUrl, shortCode);
            
            return buildShortUrl(shortCode);
        }
    }
}
```

## Challenge 2: Distributed Collision Handling

```
Problem: Multiple servers might generate same hash simultaneously

Solution: Use database UNIQUE constraint + retry

CREATE TABLE urls (
    short_code VARCHAR(8) PRIMARY KEY,  -- Enforces uniqueness
    original_url TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_original_url ON urls(original_url);
```

```java
public String shortUrl(String url) {
    int maxRetries = 3;
    int attempt = 0;
    
    while (attempt < maxRetries) {
        try {
            String shortCode = generateShortCode(url + "#" + attempt);
            database.insert(shortCode, url);  // Will fail if collision
            return buildShortUrl(shortCode);
            
        } catch (UniqueViolationException e) {
            // Collision! Retry with different salt
            attempt++;
        }
    }
    
    throw new IllegalStateException("Failed to generate unique short code");
}
```

## Challenge 3: Global Distribution

```
Problem: Users worldwide need low-latency access

Solution: GeoDNS + Regional clusters

                    GeoDNS
                      │
        ┌─────────────┼─────────────┐
        │             │             │
┌───────▼──────┐ ┌────▼─────┐ ┌────▼─────┐
│ US Cluster   │ │ EU Cluster│ │Asia Cluster│
└──────────────┘ └──────────┘ └──────────┘
        │             │             │
        └──────┬──────┴──────┬──────┘
               │             │
        ┌──────▼─────────────▼──────┐
        │   Global Database Cluster │
        │   (Multi-region replicas) │
        └───────────────────────────┘
```

## Challenge 4: Analytics at Scale



```
Problem: Tracking clicks on billions of short URLs

Solution: Event streaming + batch processing

┌──────────────┐
│ Click Event  │
└──────┬───────┘
       │
┌──────▼───────┐
│ Kafka Stream │  (High throughput, durable)
└──────┬───────┘
       │
       ├──────────┐
       │          │
┌──────▼──┐  ┌────▼────┐
│Real-time│  │ Batch   │
│Analytics│  │ Process │
│(Flink)  │  │(Spark)  │
└─────────┘  └────┬────┘
                  │
             ┌────▼────┐
             │  Data   │
             │ Lake    │
             └─────────┘
```

## Challenge 5: Rate Limiting

```java
@Component
public class RateLimiter {
    
    private final RedissonClient redisson;
    
    public boolean allowRequest(String clientId) {
        // Token bucket algorithm in Redis
        RRateLimiter limiter = redisson.getRateLimiter("limiter:" + clientId);
        
        // Configure: 100 requests per minute
        limiter.trySetRate(RateType.OVERALL, 100, 1, RateIntervalUnit.MINUTES);
        
        // Try to acquire permit
        return limiter.tryAcquire();
    }
}

@RestController
public class UrlShortenerController {
    
    @PostMapping("/shorten")
    public ResponseEntity<?> shorten(@RequestBody String url, HttpServletRequest request) {
        String clientId = getClientId(request);  // IP or API key
        
        if (!rateLimiter.allowRequest(clientId)) {
            return ResponseEntity.status(429).body("Rate limit exceeded");
        }
        
        String shortUrl = urlShortener.shortUrl(url);
        return ResponseEntity.ok(shortUrl);
    }
}
```

## Summary: Production Architecture

```
Load Balancer (Geo-aware)
        │
        ├─── API Server 1 ──┐
        ├─── API Server 2 ──┼─── Application Tier (Stateless)
        └─── API Server N ──┘
                │
        ┌───────┴───────┐
        │               │
┌───────▼─────┐  ┌──────▼──────┐
│Redis Cluster│  │Kafka Cluster│
│(Cache+Lock) │  │  (Events)   │
└───────┬─────┘  └──────┬──────┘
        │               │
┌───────▼───────────────▼──────┐
│   PostgreSQL Cluster          │
│   (Primary + Read Replicas)   │
└───────────────────────────────┘
```

---

## Conclusion

You've now seen how a production URL shortener works at every level:

1. **SHA-256** provides deterministic, collision-resistant hashing
2. **Base62** encoding creates clean, alphanumeric short codes
3. **ReadWriteLock** enables safe concurrent access with high read throughput
4. **Bidirectional mapping** provides O(1) lookups in both directions
5. **Collision handling** with rehashing ensures reliability
6. **Builder pattern** makes configuration flexible and testable
7. **Distributed architecture** enables global scale

The journey from your initial implementation to production-ready code demonstrates the importance of:
- ✅ **Deterministic behavior** over random generation
- ✅ **Proper synchronization** for thread safety
- ✅ **Comprehensive validation** for reliability
- ✅ **Bidirectional indexing** for performance
- ✅ **Collision awareness** for correctness

**Next Steps:**
- Implement database persistence
- Add analytics tracking
- Deploy to cloud with load balancing
- Monitor performance with metrics
- Scale horizontally as traffic grows
