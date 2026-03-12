package com.aistreaming.framework.worker;

import com.aistreaming.framework.domain.AiChunkMessage;
import com.aistreaming.framework.domain.MessageType;
import com.aistreaming.framework.domain.PromptTask;
import com.aistreaming.framework.messaging.MessagingBindings;
import com.aistreaming.framework.messaging.annotation.Consumer;
import com.aistreaming.framework.messaging.annotation.MessagingService;
import com.aistreaming.framework.service.AiChunkMessagingPublisher;
import com.aistreaming.framework.service.AiProviderStreamClient;
import com.aistreaming.framework.service.SessionRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.streaming.provider", name = "enabled", havingValue = "true")
@MessagingService("realAiProvider")
public class RealAiProviderWorker {

    private static final String PROVIDER_GROUP = "ai-provider-workers";

    private final AiProviderStreamClient aiProviderStreamClient;
    private final AiChunkMessagingPublisher aiChunkMessagingPublisher;
    private final SessionRegistry sessionRegistry;

    public RealAiProviderWorker(AiProviderStreamClient aiProviderStreamClient,
                                AiChunkMessagingPublisher aiChunkMessagingPublisher,
                                SessionRegistry sessionRegistry) {
        this.aiProviderStreamClient = aiProviderStreamClient;
        this.aiChunkMessagingPublisher = aiChunkMessagingPublisher;
        this.sessionRegistry = sessionRegistry;
    }

    @Consumer(binding = MessagingBindings.PROMPT_TASK, group = PROVIDER_GROUP)
    public void consume(PromptTask task) {
        AtomicInteger chunkIndex = new AtomicInteger();
        publish(task, MessageType.START, "[START]", false, chunkIndex.get());
        try {
            aiProviderStreamClient.stream(task)
                .filter(StringUtils::hasText)
                .doOnNext(token -> publish(task, MessageType.TOKEN, token, false, chunkIndex.incrementAndGet()))
                .blockLast();
            publish(task, MessageType.COMPLETE, "[DONE]", true, chunkIndex.incrementAndGet());
        } catch (Exception ex) {
            log.warn("Real AI provider worker failed to process task {}", task.getRequestId(), ex);
            publish(task, MessageType.ERROR, resolveErrorPayload(ex), true, chunkIndex.incrementAndGet());
        }
    }

    private void publish(PromptTask task, MessageType type, String payload, boolean terminal, int chunkIndex) {
        AiChunkMessage message = new AiChunkMessage();
        message.setEventId(task.getRequestId() + ":" + chunkIndex + ":" + type.name());
        message.setRequestId(task.getRequestId());
        message.setSessionId(task.getSessionId());
        message.setRouteNodeId(resolveRouteNodeId(task));
        message.setSequence(sessionRegistry.nextSequence(task.getSessionId()));
        message.setChunkIndex(chunkIndex);
        message.setType(type);
        message.setPayload(payload);
        message.setTerminal(terminal);
        message.setCreatedAt(System.currentTimeMillis());
        aiChunkMessagingPublisher.publishRaw(message);
    }

    private String resolveRouteNodeId(PromptTask task) {
        String owner = sessionRegistry.resolveOwner(task.getSessionId());
        return StringUtils.hasText(owner) ? owner : task.getRouteNodeId();
    }

    private String resolveErrorPayload(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "Upstream AI stream failed";
    }
}