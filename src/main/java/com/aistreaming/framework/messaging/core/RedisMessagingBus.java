package com.aistreaming.framework.messaging.core;

import com.aistreaming.framework.config.StreamingProperties;
import com.aistreaming.framework.service.StreamEnvelope;
import com.aistreaming.framework.support.JsonCodec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

public class RedisMessagingBus {

    private final StringRedisTemplate redisTemplate;
    private final StreamingProperties properties;
    private final JsonCodec jsonCodec;

    public RedisMessagingBus(StringRedisTemplate redisTemplate, StreamingProperties properties, JsonCodec jsonCodec) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.jsonCodec = jsonCodec;
    }

    public MessagePublishResult publish(String binding, MessagingRecord record) {
        RecordId recordId = add(streamKey(binding), jsonCodec.toJson(record));
        String streamRecordId = recordId == null ? null : recordId.getValue();
        return new MessagePublishResult(record.getMessageId(), binding, streamRecordId);
    }

    public List<StreamEnvelope<MessagingRecord>> read(String binding, String group, String consumerName) {
        String streamKey = streamKey(binding);
        ensureConsumerGroup(streamKey, group);
        List<MapRecord<String, Object, Object>> records = readRecords(streamKey, group, consumerName);
        List<StreamEnvelope<MessagingRecord>> messages = new ArrayList<StreamEnvelope<MessagingRecord>>();
        for (MapRecord<String, Object, Object> record : records) {
            Optional<String> payload = payloadOf(record);
            if (!payload.isPresent()) {
                continue;
            }
            messages.add(new StreamEnvelope<MessagingRecord>(
                streamKey,
                group,
                record.getId(),
                jsonCodec.fromJson(payload.get(), MessagingRecord.class)
            ));
        }
        return messages;
    }

    public void acknowledge(StreamEnvelope<?> envelope) {
        redisTemplate.opsForStream().acknowledge(envelope.getStreamKey(), envelope.getGroup(), envelope.getRecordId());
    }

    public String streamKey(String binding) {
        return properties.getMessagingStreamPrefix() + binding;
    }

    private RecordId add(String streamKey, String payload) {
        Map<String, String> body = Collections.singletonMap("payload", payload);
        return redisTemplate.opsForStream().add(StringRecord.of(body).withStreamKey(streamKey));
    }

    private List<MapRecord<String, Object, Object>> readRecords(String streamKey, String group, String consumerName) {
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
                add(streamKey, "{}");
                try {
                    redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), group);
                } catch (Exception nested) {
                    String nestedMessage = nested.getMessage();
                    if (nestedMessage == null || !nestedMessage.contains("BUSYGROUP")) {
                        throw nested;
                    }
                }
            } else if (message == null || !message.contains("BUSYGROUP")) {
                throw ex;
            }
        }
    }
}