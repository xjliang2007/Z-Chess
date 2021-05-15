/*
 * MIT License
 *
 * Copyright (c) 2016~2021. Z-Chess
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.isahl.chess.rook.storage.cache.config;

import com.isahl.chess.king.base.log.Logger;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.jsr107.Eh107Configuration;
import org.ehcache.xml.XmlConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import javax.cache.Cache;
import javax.cache.CacheManager;
import java.net.URL;
import java.time.Duration;
import java.util.Objects;

/**
 * @author william.d.zk
 * @date 2020/6/6
 */
@Configuration
@EnableCaching
@PropertySource({"classpath:cache.properties"})
public class EhcacheConfig
{

    private final Logger _Logger = Logger.getLogger("rook.cache." + getClass().getSimpleName());

    private static XmlConfiguration _RookConfig;
    private static final String     DEFAULT_TEMPLATE_NAME = "rook-default";

    public EhcacheConfig()
    {
        URL ehcaheUrl = Objects.requireNonNull(getClass().getResource("/ehcache.xml"));
        _RookConfig = new XmlConfiguration(ehcaheUrl);
    }

    public static <K,
                   V> Cache<K,
                            V> createCache(CacheManager cacheManager,
                                           String cacheName,
                                           Class<K> keyType,
                                           Class<V> valueType,
                                           Duration expiry) throws ClassNotFoundException,
                                                            InstantiationException,
                                                            IllegalAccessException
    {
        CacheConfigurationBuilder<K,
                                  V> builder = _RookConfig.newCacheConfigurationBuilderFromTemplate(DEFAULT_TEMPLATE_NAME,
                                                                                                    keyType,
                                                                                                    valueType);
        return cacheManager.createCache(cacheName,
                                        Eh107Configuration.fromEhcacheCacheConfiguration(builder.withExpiry(ExpiryPolicyBuilder.timeToIdleExpiration(expiry))
                                                                                                .build()));
    }
}