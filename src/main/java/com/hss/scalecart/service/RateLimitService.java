package com.hss.scalecart.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;
import io.github.bucket4j.Bucket;
import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Duration;
import java.util.function.Supplier;

@Service
@Slf4j
public class RateLimitService {

    private final ProxyManager<String> proxyManager;

    public RateLimitService(LettuceConnectionFactory lettuceConnectionFactory) {
        io.lettuce.core.RedisClient nativeClient = (io.lettuce.core.RedisClient) lettuceConnectionFactory.getNativeClient();

        StatefulRedisConnection<String, byte[]> connection =
                nativeClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .build();
    }

    public Bucket resolveOrderBucket(String customerId) {
        String key = "rl:order:" + customerId;
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(10)
                        .refillGreedy(10, Duration.ofMinutes(1))
                        .build())
                .build();
        return proxyManager.builder().build(key, configSupplier);
    }

    public Bucket resolveLoginBucket(String ip) {
        String key = "rl:login:" + ip;
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5)
                        .refillGreedy(5, Duration.ofMinutes(1))
                        .build())
                .build();
        return proxyManager.builder().build(key, configSupplier);
    }

    public Bucket resolveProductBucket(String sellerId) {
        String key = "rl:product:" + sellerId;
        Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(20)
                        .refillGreedy(20, Duration.ofMinutes(1))
                        .build())
                .build();
        return proxyManager.builder().build(key, configSupplier);
    }
}