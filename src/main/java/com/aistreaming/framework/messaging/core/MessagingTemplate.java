package com.aistreaming.framework.messaging.core;

import com.aistreaming.framework.support.JsonCodec;
import java.util.UUID;

public class MessagingTemplate {

    private final RedisMessagingBus messagingBus;
    private final JsonCodec jsonCodec;

    public MessagingTemplate(RedisMessagingBus messagingBus, JsonCodec jsonCodec) {
        this.messagingBus = messagingBus;
        this.jsonCodec = jsonCodec;
    }

    public MessagePublishResult publish(String serviceName, String operation, String binding, Object payload) {
        MessagingRecord record = new MessagingRecord();
        record.setMessageId(UUID.randomUUID().toString());
        record.setService(serviceName);
        record.setOperation(operation);
        record.setBinding(binding);
        record.setPayloadType(payload == null ? null : payload.getClass().getName());
        record.setPayload(payload == null ? null : jsonCodec.toJson(payload));
        record.setCreatedAt(System.currentTimeMillis());
        return messagingBus.publish(binding, record);
    }
}
