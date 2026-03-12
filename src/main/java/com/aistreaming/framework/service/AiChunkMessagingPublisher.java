package com.aistreaming.framework.service;

import com.aistreaming.framework.domain.AiChunkMessage;
import com.aistreaming.framework.messaging.MessagingBindings;
import com.aistreaming.framework.messaging.annotation.MessagingService;
import com.aistreaming.framework.messaging.annotation.Publisher;
import com.aistreaming.framework.messaging.core.MessagePublishResult;
import org.springframework.stereotype.Service;

@Service
@MessagingService("aiPipeline")
public class AiChunkMessagingPublisher {

    @Publisher(binding = MessagingBindings.AI_CHUNK_RAW, invokeTarget = false)
    public MessagePublishResult publishRaw(AiChunkMessage message) {
        return null;
    }

    @Publisher(binding = MessagingBindings.AI_CHUNK_PROCESSED, invokeTarget = false)
    public MessagePublishResult publishProcessed(AiChunkMessage message) {
        return null;
    }
}