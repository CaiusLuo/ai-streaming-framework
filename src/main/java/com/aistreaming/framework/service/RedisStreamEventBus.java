package com.aistreaming.framework.service;

import com.aistreaming.framework.config.StreamingProperties;
import com.aistreaming.framework.domain.PromptTask;
import com.aistreaming.framework.domain.StreamEvent;
import com.aistreaming.framework.support.JsonCodec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RedisStreamEventBus {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamEventBus.class);
    private static final DefaultRedisScript<Long> TRIM_HISTORY_SCRIPT =
        new DefaultRedisScript<Long>(
            "redis.call('XADD', KEYS[1], '*', 'payload', ARGV[1]); return redis.call('XTRIM', KEYS[1], 'MAXLEN', '~', ARGV[2]);",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final StreamingProperties properties;
    private final JsonCodec jsonCodec;

    public RedisStreamEventBus(StringRedisTemplate redisTemplate, StreamingProperties properties, JsonCodec jsonCodec) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.jsonCodec = jsonCodec;
        ensureConsumerGroup(properties.getWorkerStreamKey(), properties.getWorkerConsumerGroup());
        ensureConsumerGroup(gatewayStreamKey(properties.getNodeId()), gatewayGroup(properties.getNodeId()));
    }

    public RecordId publishPromptTask(PromptTask task) {
        return add(properties.getWorkerStreamKey(), jsonCodec.toJson(task));
    }

    public RecordId publishGatewayEvent(String nodeId, StreamEvent event) {
        return add(gatewayStreamKey(nodeId), jsonCodec.toJson(event));
    }

    public void appendHistory(StreamEvent event) {
        List<String> keys = Collections.singletonList(historyStreamKey(event.getSessionId()));
        List<String> args = new ArrayList<String>();
        args.add(jsonCodec.toJson(event));
        args.add(String.valueOf(properties.getHistoryMaxLength()));
        redisTemplate.execute(TRIM_HISTORY_SCRIPT, keys, args.toArray(new String[0]));
    }

    public List<StreamEnvelope<PromptTask>> readPromptTasks(String consumerName) {
        List<MapRecord<String, Object, Object>> records = read(
            properties.getWorkerStreamKey(),
            properties.getWorkerConsumerGroup(),
            consumerName
        );
        List<StreamEnvelope<PromptTask>> tasks = new ArrayList<StreamEnvelope<PromptTask>>();
        for (MapRecord<String, Object, Object> record : records) {
            Optional<String> payload = payloadOf(record);
            if (payload.isPresent()) {
                tasks.add(new StreamEnvelope<PromptTask>(
                    properties.getWorkerStreamKey(),
                    properties.getWorkerConsumerGroup(),
                    record.getId(),
                    jsonCodec.fromJson(payload.get(), PromptTask.class)
                ));
            }
        }
        return tasks;
    }

    public List<StreamEnvelope<StreamEvent>> readGatewayEvents(String nodeId, String consumerName) {
        String streamKey = gatewayStreamKey(nodeId);
        String group = gatewayGroup(nodeId);
        ensureConsumerGroup(streamKey, group);
        List<MapRecord<String, Object, Object>> records = read(streamKey, group, consumerName);
        List<StreamEnvelope<StreamEvent>> events = new ArrayList<StreamEnvelope<StreamEvent>>();
        for (MapRecord<String, Object, Object> record : records) {
            Optional<String> payload = payloadOf(record);
            if (payload.isPresent()) {
                events.add(new StreamEnvelope<StreamEvent>(
                    streamKey,
                    group,
                    record.getId(),
                    jsonCodec.fromJson(payload.get(), StreamEvent.class)
                ));
            }
        }
        return events;
    }

    public void acknowledge(StreamEnvelope<?> envelope) {
        redisTemplate.opsForStream().acknowledge(envelope.getStreamKey(), envelope.getGroup(), envelope.getRecordId());
    }

    public List<StreamEvent> loadHistory(String sessionId, long afterSequence) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
            .range(historyStreamKey(sessionId), Range.unbounded());
        List<StreamEvent> events = new ArrayList<StreamEvent>();
        if (records == null) {
            return events;
        }
        for (MapRecord<String, Object, Object> record : records) {
            Optional<String> payload = payloadOf(record);
            if (!payload.isPresent()) {
                continue;
            }
            StreamEvent event = jsonCodec.fromJson(payload.get(), StreamEvent.class);
            if (event.getSequence() > afterSequence) {
                events.add(event);
            }
        }
        events.sort(Comparator.comparingLong(StreamEvent::getSequence));
        return events;
    }

    public String gatewayStreamKey(String nodeId) {
        return properties.getGatewayStreamPrefix() + nodeId;
    }

    public String gatewayGroup(String nodeId) {
        return properties.getGatewayGroupPrefix() + nodeId;
    }

    private RecordId add(String streamKey, String payload) {
        Map<String, String> body = Collections.singletonMap("payload", payload);
        return redisTemplate.opsForStream().add(StringRecord.of(body).withStreamKey(streamKey));
    }

    private List<MapRecord<String, Object, Object>> read(String streamKey, String group, String consumerName) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
            Consumer.from(group, consumerName),
            StreamReadOptions.empty()
                .count(properties.getPollCount())
                .block(Duration.ofMillis(properties.getPollTimeoutMillis())),
            StreamOffset.create(streamKey, ReadOffset.lastConsumed())
        );
        return records == null ? Collections.<MapRecord<String, Object, Object>>emptyList() : records;
    }

    private Optional<String> payloadOf(MapRecord<String, Object, Object> record) {
        Object payload = record.getValue().get("payload");
        if (payload == null || !StringUtils.hasText(payload.toString())) {
            return Optional.empty();
        }
        return Optional.of(payload.toString());
    }

    private void ensureConsumerGroup(String streamKey, String group) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), group);
        } catch (Exception ex) {
            String message = ex.getMessage();
            if (message != null && message.contains("no such key")) {
                add(streamKey, "");
                try {
                    redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), group);
                } catch (Exception nested) {
                    String nestedMessage = nested.getMessage();
                    if (nestedMessage == null || !nestedMessage.contains("BUSYGROUP")) {
                        throw nested;
                    }
                }
            } else if (message == null || !message.contains("BUSYGROUP")) {
                log.debug("Create group skipped for stream {}", streamKey, ex);
            }
        }
    }

    private String historyStreamKey(String sessionId) {
        return properties.getSessionHistoryPrefix() + sessionId;
    }
}