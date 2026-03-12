package com.aistreaming.framework.worker;

import com.aistreaming.framework.config.StreamingProperties;
import com.aistreaming.framework.domain.MessageType;
import com.aistreaming.framework.domain.PromptTask;
import com.aistreaming.framework.domain.StreamEvent;
import com.aistreaming.framework.messaging.MessagingBindings;
import com.aistreaming.framework.messaging.annotation.Consumer;
import com.aistreaming.framework.messaging.annotation.MessagingService;
import com.aistreaming.framework.service.RedisStreamEventBus;
import com.aistreaming.framework.service.SessionRegistry;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@Profile("mock")
@MessagingService("aiWorker")
public class MockAiWorker {

    private final RedisStreamEventBus eventBus;
    private final SessionRegistry sessionRegistry;
    private final StreamingProperties properties;

    public MockAiWorker(RedisStreamEventBus eventBus,
                        SessionRegistry sessionRegistry,
                        StreamingProperties properties) {
        this.eventBus = eventBus;
        this.sessionRegistry = sessionRegistry;
        this.properties = properties;
    }

    @Consumer(binding = MessagingBindings.PROMPT_TASK)
    public void consume(PromptTask task) {
        try {
            processTask(task);
        } catch (Exception ex) {
            log.warn("Mock AI worker failed to process task {}", task.getRequestId(), ex);
            throw ex;
        }
    }

    private void processTask(PromptTask task) {
        List<String> tokens = tokenize(task.getPrompt());
        long baseSequence = sessionRegistry.reserveSequenceBlock(task.getSessionId(), task.getRequestId(), tokens.size() + 2);
        publish(task, MessageType.START, "[START]", false, baseSequence, 0);
        for (int i = 0; i < tokens.size(); i++) {
            publish(task, MessageType.TOKEN, tokens.get(i), false, baseSequence + i + 1, i + 1);
            quietSleep(properties.getMockTokenDelayMillis());
        }
        publish(task, MessageType.COMPLETE, "[DONE]", true, baseSequence + tokens.size() + 1, tokens.size() + 1);
    }

    private void publish(PromptTask task, MessageType type, String payload, boolean terminal, long sequence, int chunkIndex) {
        StreamEvent event = new StreamEvent();
        event.setEventId(task.getRequestId() + ":" + chunkIndex + ":" + type.name());
        event.setRequestId(task.getRequestId());
        event.setSessionId(task.getSessionId());
        String owner = sessionRegistry.resolveOwner(task.getSessionId());
        event.setRouteNodeId(StringUtils.hasText(owner) ? owner : task.getRouteNodeId());
        event.setSequence(sequence);
        event.setType(type);
        event.setPayload(payload);
        event.setTerminal(terminal);
        event.setCreatedAt(System.currentTimeMillis());
        eventBus.appendHistory(event);
        eventBus.publishGatewayEvent(event.getRouteNodeId(), event);
    }

    private List<String> tokenize(String prompt) {
        String normalized = "AI streaming response for: " + prompt;
        String[] parts = normalized.split(" ");
        List<String> tokens = new ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].trim().isEmpty()) {
                continue;
            }
            tokens.add(parts[i] + (i == parts.length - 1 ? "" : " "));
        }
        return tokens;
    }

    private void quietSleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}