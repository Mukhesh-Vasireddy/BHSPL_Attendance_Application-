package com.bhspl.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe utility for caching commonly used data.
 * Supports Time-To-Live (TTL) eviction.
 */
public class CacheManager {
    private static final CacheManager INSTANCE = new CacheManager();

    public static CacheManager getInstance() {
        return INSTANCE;
    }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private CacheManager() {}

    /**
     * Put a value in the cache with a specified Time-To-Live (TTL) in milliseconds.
     */
    public void put(String key, Object value, long ttlMs) {
        if (value == null) {
            cache.remove(key);
        } else {
            cache.put(key, new CacheEntry(value, ttlMs));
        }
    }

    /**
     * Get a value from the cache. Returns null if key is missing or expired.
     */
    public Object get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            return null;
        }
        return entry.value;
    }

    /**
     * Invalidate a specific key.
     */
    public void invalidate(String key) {
        cache.remove(key);
    }

    /**
     * Invalidate all keys.
     */
    public void clear() {
        cache.clear();
    }

    private static class CacheEntry {
        final Object value;
        final long expiresAt;

        CacheEntry(Object value, long ttlMs) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
