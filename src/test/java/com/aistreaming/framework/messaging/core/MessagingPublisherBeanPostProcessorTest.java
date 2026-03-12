package com.aistreaming.framework.messaging.core;

import com.aistreaming.framework.messaging.annotation.MessagingService;
import com.aistreaming.framework.messaging.annotation.Publisher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingPublisherBeanPostProcessorTest {

    @Test
    void shouldPublishWithoutInvokingTargetWhenConfigured() {
        MessagingTemplate messagingTemplate = mock(MessagingTemplate.class);
        MessagePublishResult publishResult = new MessagePublishResult("msg-1", "demo.binding", "1-0");
        when(messagingTemplate.publish("demoService", "send", "demo.binding", "payload")).thenReturn(publishResult);

        DemoPublisher target = new DemoPublisher();
        DemoPublisher proxy = (DemoPublisher) new MessagingPublisherBeanPostProcessor(messagingTemplate)
            .postProcessAfterInitialization(target, "demoPublisher");

        MessagePublishResult result = proxy.send("payload");

        Assertions.assertSame(publishResult, result);
        Assertions.assertFalse(target.invoked);
        verify(messagingTemplate).publish("demoService", "send", "demo.binding", "payload");
    }

    @Test
    void shouldInvokeTargetBeforePublishingByDefault() {
        MessagingTemplate messagingTemplate = mock(MessagingTemplate.class);
        when(messagingTemplate.publish("demoService", "process", "demoService.process", "payload"))
            .thenReturn(new MessagePublishResult("msg-2", "demoService.process", "1-1"));

        DemoPublisher target = new DemoPublisher();
        DemoPublisher proxy = (DemoPublisher) new MessagingPublisherBeanPostProcessor(messagingTemplate)
            .postProcessAfterInitialization(target, "demoPublisher");

        String result = proxy.process("payload");

        Assertions.assertEquals("processed:payload", result);
        Assertions.assertTrue(target.invoked);
        verify(messagingTemplate).publish("demoService", "process", "demoService.process", "payload");
    }

    @MessagingService("demoService")
    static class DemoPublisher {

        private boolean invoked;

        @Publisher(binding = "demo.binding", invokeTarget = false)
        public MessagePublishResult send(String payload) {
            invoked = true;
            return null;
        }

        @Publisher
        public String process(String payload) {
            invoked = true;
            return "processed:" + payload;
        }
    }
}