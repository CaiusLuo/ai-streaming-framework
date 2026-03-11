package com.aistreaming.framework.consumer;

import com.aistreaming.framework.config.StreamingProperties;
import com.aistreaming.framework.domain.StreamEvent;
import com.aistreaming.framework.service.OrderedEventProcessor;
import com.aistreaming.framework.service.RedisStreamEventBus;
import com.aistreaming.framework.service.SessionSinkHub;
import com.aistreaming.framework.service.StreamEnvelope;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GatewayEventConsumer {

    private final RedisStreamEventBus eventBus;
    private final OrderedEventProcessor orderedEventProcessor;
    private final SessionSinkHub sessionSinkHub;
    private final StreamingProperties properties;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    public GatewayEventConsumer(RedisStreamEventBus eventBus,
                                OrderedEventProcessor orderedEventProcessor,
                                SessionSinkHub sessionSinkHub,
                                StreamingProperties properties) {
        this.eventBus = eventBus;
        this.orderedEventProcessor = orderedEventProcessor;
        this.sessionSinkHub = sessionSinkHub;
        this.properties = properties;
    }

    @PostConstruct
    public void start() {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        List<StreamEnvelope<StreamEvent>> envelopes = eventBus.readGatewayEvents(
                            properties.getNodeId(),
                            properties.getNodeId() + "-gateway-consumer"
                        );
                        for (StreamEnvelope<StreamEvent> envelope : envelopes) {
                            orderedEventProcessor.process(envelope.getPayload(), sessionSinkHub::emit);
                            eventBus.acknowledge(envelope);
                        }
                    } catch (Exception ex) {
                        log.warn("Gateway event consumer loop failed", ex);
                    }
                }
            }
        });
    }

    @PreDestroy
    public void stop() {
        running = false;
        executor.shutdownNow();
    }
}