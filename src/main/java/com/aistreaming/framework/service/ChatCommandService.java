package com.aistreaming.framework.service;

import com.aistreaming.framework.domain.ChatRequest;
import com.aistreaming.framework.domain.PromptTask;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatCommandService {

    private final SessionRegistry sessionRegistry;
    private final PromptTaskMessagingPublisher promptTaskMessagingPublisher;

    public ChatCommandService(SessionRegistry sessionRegistry,
                              PromptTaskMessagingPublisher promptTaskMessagingPublisher) {
        this.sessionRegistry = sessionRegistry;
        this.promptTaskMessagingPublisher = promptTaskMessagingPublisher;
    }

    public Map<String, String> submit(ChatRequest request) {
        String sessionId = request.getSessionId();
        String owner = sessionRegistry.resolveOwner(sessionId);
        if (!StringUtils.hasText(owner)) {
            owner = sessionRegistry.claim(sessionId);
        }

        PromptTask task = new PromptTask();
        task.setRequestId(StringUtils.hasText(request.getRequestId()) ? request.getRequestId() : UUID.randomUUID().toString());
        task.setSessionId(sessionId);
        task.setPrompt(request.getPrompt());
        task.setRouteNodeId(owner);
        task.setCreatedAt(System.currentTimeMillis());
        promptTaskMessagingPublisher.publish(task);

        Map<String, String> response = new LinkedHashMap<String, String>();
        response.put("requestId", task.getRequestId());
        response.put("sessionId", task.getSessionId());
        response.put("routeNodeId", owner);
        response.put("status", "QUEUED");
        return response;
    }
}
