package com.aistreaming.framework.worker;

import com.aistreaming.framework.domain.AiChunkMessage;
import com.aistreaming.framework.domain.MessageType;
import com.aistreaming.framework.domain.StreamEvent;
import com.aistreaming.framework.service.RedisStreamEventBus;
import com.aistreaming.framework.service.SessionRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChunkDeliveryWorkerTest {

    @Test
    void shouldConvertProcessedChunkIntoGatewayEvent() {
        RedisStreamEventBus eventBus = mock(RedisStreamEventBus.class);
        SessionRegistry sessionRegistry = mock(SessionRegistry.class);
        AiChunkDeliveryWorker worker = new AiChunkDeliveryWorker(eventBus, sessionRegistry);
        AiChunkMessage processedMessage = new AiChunkMessage();
        processedMessage.setEventId("request-1:1:TOKEN");
        processedMessage.setRequestId("request-1");
        processedMessage.setSessionId("session-1");
        processedMessage.setRouteNodeId("gateway-1");
        processedMessage.setSequence(2L);
        processedMessage.setChunkIndex(1);
        processedMessage.setType(MessageType.TOKEN);
        processedMessage.setPayload("Hello ");
        processedMessage.setTerminal(false);
        processedMessage.setCreatedAt(123L);

        when(sessionRegistry.resolveOwner("session-1")).thenReturn("gateway-2");

        worker.consume(processedMessage);

        ArgumentCaptor<StreamEvent> captor = ArgumentCaptor.forClass(StreamEvent.class);
        verify(eventBus).appendHistory(captor.capture());
        StreamEvent event = captor.getValue();

        Assertions.assertEquals(processedMessage.getEventId(), event.getEventId());
        Assertions.assertEquals(processedMessage.getPayload(), event.getPayload());
        Assertions.assertEquals("gateway-2", event.getRouteNodeId());
        verify(eventBus).publishGatewayEvent(eq("gateway-2"), eq(event));
    }
}