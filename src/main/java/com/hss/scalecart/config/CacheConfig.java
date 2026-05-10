package com.hss.scalecart.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache GET failed, falling through to DB: {}", e.getMessage());
            }
            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed: {}", e.getMessage());
            }
            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache EVICT failed: {}", e.getMessage());
            }
            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache CLEAR failed: {}", e.getMessage());
            }
        };
    }
}