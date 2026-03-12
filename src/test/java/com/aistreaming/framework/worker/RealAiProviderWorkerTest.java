package com.aistreaming.framework.worker;

import com.aistreaming.framework.domain.AiChunkMessage;
import com.aistreaming.framework.domain.MessageType;
import com.aistreaming.framework.domain.PromptTask;
import com.aistreaming.framework.service.AiChunkMessagingPublisher;
import com.aistreaming.framework.service.AiProviderStreamClient;
import com.aistreaming.framework.service.SessionRegistry;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealAiProviderWorkerTest {

    @Test
    void shouldPublishStartTokensAndCompleteMessages() {
        AiProviderStreamClient streamClient = mock(AiProviderStreamClient.class);
        AiChunkMessagingPublisher publisher = mock(AiChunkMessagingPublisher.class);
        SessionRegistry sessionRegistry = mock(SessionRegistry.class);
        RealAiProviderWorker worker = new RealAiProviderWorker(streamClient, publisher, sessionRegistry);
        PromptTask task = promptTask();

        when(streamClient.stream(task)).thenReturn(Flux.just("Hello ", "world"));
        when(sessionRegistry.resolveOwner(task.getSessionId())).thenReturn(task.getRouteNodeId());
        when(sessionRegistry.nextSequence(task.getSessionId())).thenReturn(1L, 2L, 3L, 4L);

        worker.consume(task);

        ArgumentCaptor<AiChunkMessage> captor = ArgumentCaptor.forClass(AiChunkMessage.class);
        verify(publisher, times(4)).publishRaw(captor.capture());
        List<AiChunkMessage> messages = captor.getAllValues();

        Assertions.assertEquals(MessageType.START, messages.get(0).getType());
        Assertions.assertEquals(MessageType.TOKEN, messages.get(1).getType());
        Assertions.assertEquals("Hello ", messages.get(1).getPayload());
        Assertions.assertEquals(MessageType.TOKEN, messages.get(2).getType());
        Assertions.assertEquals("world", messages.get(2).getPayload());
        Assertions.assertEquals(MessageType.COMPLETE, messages.get(3).getType());
        Assertions.assertTrue(messages.get(3).isTerminal());
        Assertions.assertEquals(4L, messages.get(3).getSequence());
    }

    @Test
    void shouldPublishErrorMessageWhenUpstreamStreamFails() {
        AiProviderStreamClient streamClient = mock(AiProviderStreamClient.class);
        AiChunkMessagingPublisher publisher = mock(AiChunkMessagingPublisher.class);
        SessionRegistry sessionRegistry = mock(SessionRegistry.class);
        RealAiProviderWorker worker = new RealAiProviderWorker(streamClient, publisher, sessionRegistry);
        PromptTask task = promptTask();

        when(streamClient.stream(task)).thenReturn(Flux.error(new IllegalStateException("upstream failed")));
        when(sessionRegistry.resolveOwner(task.getSessionId())).thenReturn(task.getRouteNodeId());
        when(sessionRegistry.nextSequence(task.getSessionId())).thenReturn(1L, 2L);

        worker.consume(task);

        ArgumentCaptor<AiChunkMessage> captor = ArgumentCaptor.forClass(AiChunkMessage.class);
        verify(publisher, times(2)).publishRaw(captor.capture());
        List<AiChunkMessage> messages = captor.getAllValues();

        Assertions.assertEquals(MessageType.START, messages.get(0).getType());
        Assertions.assertEquals(MessageType.ERROR, messages.get(1).getType());
        Assertions.assertTrue(messages.get(1).isTerminal());
        Assertions.assertEquals("upstream failed", messages.get(1).getPayload());
    }

    private PromptTask promptTask() {
        PromptTask task = new PromptTask();
        task.setRequestId("request-1");
        task.setSessionId("session-1");
        task.setPrompt("Say hello");
        task.setRouteNodeId("gateway-1");
        return task;
    }
}