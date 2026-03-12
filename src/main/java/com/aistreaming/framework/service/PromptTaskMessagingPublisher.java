package com.aistreaming.framework.service;

import com.aistreaming.framework.domain.PromptTask;
import com.aistreaming.framework.messaging.MessagingBindings;
import com.aistreaming.framework.messaging.annotation.MessagingService;
import com.aistreaming.framework.messaging.annotation.Publisher;
import com.aistreaming.framework.messaging.core.MessagePublishResult;
import org.springframework.stereotype.Service;

@Service
@MessagingService("aiWorker")
public class PromptTaskMessagingPublisher {

    @Publisher(binding = MessagingBindings.PROMPT_TASK, invokeTarget = false)
    public MessagePublishResult publish(PromptTask task) {
        return null;
    }
}
