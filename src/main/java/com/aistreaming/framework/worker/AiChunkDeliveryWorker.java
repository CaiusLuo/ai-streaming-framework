package com.aistreaming.framework.worker;

import com.aistreaming.framework.domain.AiChunkMessage;
import com.aistreaming.framework.domain.StreamEvent;
import com.aistreaming.framework.messaging.MessagingBindings;
import com.aistreaming.framework.messaging.annotation.Consumer;
import com.aistreaming.framework.messaging.annotation.MessagingService;
import com.aistreaming.framework.service.RedisStreamEventBus;
import com.aistreaming.framework.service.SessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.streaming.provider", name = "enabled", havingValue = "true")
@MessagingService("aiChunkDelivery")
public class AiChunkDeliveryWorker {

    private static final String DELIVERY_GROUP = "ai-chunk-delivery";

    private final RedisStreamEventBus eventBus;
    private final SessionRegistry sessionRegistry;

    public AiChunkDeliveryWorker(RedisStreamEventBus eventBus, SessionRegistry sessionRegistry) {
        this.eventBus = eventBus;
        this.sessionRegistry = sessionRegistry;
    }

    @Consumer(binding = MessagingBindings.AI_CHUNK_PROCESSED, group = DELIVERY_GROUP)
    public void consume(AiChunkMessage processedMessage) {
        StreamEvent event = toStreamEvent(processedMessage);
        eventBus.appendHistory(event);
        eventBus.publishGatewayEvent(event.getRouteNodeId(), event);
    }

    private StreamEvent toStreamEvent(AiChunkMessage message) {
        StreamEvent event = new StreamEvent();
        event.setEventId(message.getEventId());
        event.setRequestId(message.getRequestId());
        event.setSessionId(message.getSessionId());
        event.setRouteNodeId(resolveRouteNodeId(message));
        event.setSequence(message.getSequence());
        event.setType(message.getType());
        event.setPayload(message.getPayload());
        event.setTerminal(message.isTerminal());
        event.setCreatedAt(message.getCreatedAt());
        return event;
    }

    private String resolveRouteNodeId(AiChunkMessage message) {
        String owner = sessionRegistry.resolveOwner(message.getSessionId());
        return StringUtils.hasText(owner) ? owner : message.getRouteNodeId();
    }
}