package com.logistpulse.lab.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Parte 8.3: la caché es una optimización, no una dependencia. Si Redis falla al leer o escribir una ruta, se registra
 * un WARN y el método se ejecuta igual (se calcula la ruta con A*), en vez de propagar el error y devolver un 500.
 */
@Configuration
public class CacheResilienceConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }
}
