package com.aistreaming.framework.worker;

import com.aistreaming.framework.domain.AiChunkMessage;
import com.aistreaming.framework.domain.MessageType;
import com.aistreaming.framework.service.AiChunkMessagingPublisher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiChunkPostProcessorWorkerTest {

    @Test
    void shouldForwardRawChunkIntoProcessedBinding() {
        AiChunkMessagingPublisher publisher = mock(AiChunkMessagingPublisher.class);
        AiChunkPostProcessorWorker worker = new AiChunkPostProcessorWorker(publisher);
        AiChunkMessage rawMessage = new AiChunkMessage();
        rawMessage.setEventId("request-1:1:TOKEN");
        rawMessage.setRequestId("request-1");
        rawMessage.setSessionId("session-1");
        rawMessage.setRouteNodeId("gateway-1");
        rawMessage.setSequence(2L);
        rawMessage.setChunkIndex(1);
        rawMessage.setType(MessageType.TOKEN);
        rawMessage.setPayload("Hello ");
        rawMessage.setTerminal(false);
        rawMessage.setCreatedAt(123L);

        worker.consume(rawMessage);

        ArgumentCaptor<AiChunkMessage> captor = ArgumentCaptor.forClass(AiChunkMessage.class);
        verify(publisher).publishProcessed(captor.capture());
        AiChunkMessage processedMessage = captor.getValue();

        Assertions.assertEquals(rawMessage.getEventId(), processedMessage.getEventId());
        Assertions.assertEquals(rawMessage.getPayload(), processedMessage.getPayload());
        Assertions.assertEquals(rawMessage.getSequence(), processedMessage.getSequence());
        Assertions.assertEquals(rawMessage.getType(), processedMessage.getType());
    }
}