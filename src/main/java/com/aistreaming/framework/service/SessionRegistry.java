package com.aistreaming.framework.service;

import com.aistreaming.framework.config.StreamingProperties;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SessionRegistry {

    private final StringRedisTemplate redisTemplate;
    private final StreamingProperties properties;
    private final Map<String, AtomicInteger> localSessions = new ConcurrentHashMap<String, AtomicInteger>();

    public SessionRegistry(StringRedisTemplate redisTemplate, StreamingProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public String register(String sessionId) {
        localSessions.computeIfAbsent(sessionId, key -> new AtomicInteger()).incrementAndGet();
        bind(sessionId, properties.getNodeId());
        return properties.getNodeId();
    }

    public void release(String sessionId) {
        AtomicInteger counter = localSessions.get(sessionId);
        if (counter == null) {
            return;
        }
        if (counter.decrementAndGet() <= 0) {
            localSessions.remove(sessionId);
            String key = routeKey(sessionId);
            String owner = redisTemplate.opsForValue().get(key);
            if (properties.getNodeId().equals(owner)) {
                redisTemplate.delete(key);
            }
        }
    }

    public String resolveOwner(String sessionId) {
        String owner = redisTemplate.opsForValue().get(routeKey(sessionId));
        if (!StringUtils.hasText(owner) && localSessions.containsKey(sessionId)) {
            return properties.getNodeId();
        }
        return owner;
    }

    public String claim(String sessionId) {
        bind(sessionId, properties.getNodeId());
        return properties.getNodeId();
    }

    public long nextSequence(String sessionId) {
        Long value = redisTemplate.opsForValue().increment(sequenceKey(sessionId));
        redisTemplate.expire(sequenceKey(sessionId), Duration.ofSeconds(properties.getSessionTtlSeconds()));
        return value == null ? 1L : value.longValue();
    }

    public long reserveSequenceBlock(String sessionId, String requestId, int blockSize) {
        String key = requestSequenceKey(requestId);
        String existing = redisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(existing)) {
            return Long.parseLong(existing);
        }

        Long end = redisTemplate.opsForValue().increment(sequenceKey(sessionId), blockSize);
        long last = end == null ? blockSize : end.longValue();
        long start = last - blockSize + 1;
        Boolean stored = redisTemplate.opsForValue().setIfAbsent(
            key,
            String.valueOf(start),
            Duration.ofSeconds(properties.getSessionTtlSeconds())
        );
        if (Boolean.FALSE.equals(stored)) {
            String winner = redisTemplate.opsForValue().get(key);
            if (StringUtils.hasText(winner)) {
                return Long.parseLong(winner);
            }
        }
        redisTemplate.expire(sequenceKey(sessionId), Duration.ofSeconds(properties.getSessionTtlSeconds()));
        return start;
    }

    public Set<String> localSessionIds() {
        return localSessions.keySet();
    }

    @Scheduled(fixedDelayString = "${ai.streaming.route-refresh-millis:30000}")
    public void refreshRoutes() {
        for (String sessionId : localSessions.keySet()) {
            bind(sessionId, properties.getNodeId());
        }
    }

    private void bind(String sessionId, String nodeId) {
        redisTemplate.opsForValue().set(routeKey(sessionId), nodeId,
            Duration.ofSeconds(properties.getSessionTtlSeconds()));
    }

    private String routeKey(String sessionId) {
        return properties.getSessionRoutePrefix() + sessionId;
    }

    private String sequenceKey(String sessionId) {
        return properties.getSessionSequencePrefix() + sessionId;
    }

    private String requestSequenceKey(String requestId) {
        return properties.getSessionSequencePrefix() + "request:" + requestId;
    }
}