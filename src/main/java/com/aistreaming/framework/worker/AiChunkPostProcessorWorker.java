package com.aistreaming.framework.worker;

import com.aistreaming.framework.domain.AiChunkMessage;
import com.aistreaming.framework.messaging.MessagingBindings;
import com.aistreaming.framework.messaging.annotation.Consumer;
import com.aistreaming.framework.messaging.annotation.MessagingService;
import com.aistreaming.framework.service.AiChunkMessagingPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.streaming.provider", name = "enabled", havingValue = "true")
@MessagingService("aiChunkPostProcessor")
public class AiChunkPostProcessorWorker {

    private static final String PROCESSOR_GROUP = "ai-chunk-processor";

    private final AiChunkMessagingPublisher aiChunkMessagingPublisher;

    public AiChunkPostProcessorWorker(AiChunkMessagingPublisher aiChunkMessagingPublisher) {
        this.aiChunkMessagingPublisher = aiChunkMessagingPublisher;
    }

    @Consumer(binding = MessagingBindings.AI_CHUNK_RAW, group = PROCESSOR_GROUP)
    public void consume(AiChunkMessage rawMessage) {
        aiChunkMessagingPublisher.publishProcessed(copy(rawMessage));
    }

    private AiChunkMessage copy(AiChunkMessage source) {
        AiChunkMessage target = new AiChunkMessage();
        target.setEventId(source.getEventId());
        target.setRequestId(source.getRequestId());
        target.setSessionId(source.getSessionId());
        target.setRouteNodeId(source.getRouteNodeId());
        target.setSequence(source.getSequence());
        target.setChunkIndex(source.getChunkIndex());
        target.setType(source.getType());
        target.setPayload(source.getPayload());
        target.setTerminal(source.isTerminal());
        target.setCreatedAt(source.getCreatedAt());
        return target;
    }
}