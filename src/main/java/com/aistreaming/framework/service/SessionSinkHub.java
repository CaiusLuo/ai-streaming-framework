package com.aistreaming.framework.service;

import com.aistreaming.framework.config.StreamingProperties;
import com.aistreaming.framework.domain.DeliveryMode;
import com.aistreaming.framework.domain.SessionRegistration;
import com.aistreaming.framework.domain.StreamEvent;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class SessionSinkHub {

    private final Map<String, SessionChannel> channels = new ConcurrentHashMap<String, SessionChannel>();
    private final SessionRegistry sessionRegistry;
    private final RedisStreamEventBus eventBus;
    private final StreamingProperties properties;

    public SessionSinkHub(SessionRegistry sessionRegistry,
                          RedisStreamEventBus eventBus,
                          StreamingProperties properties) {
        this.sessionRegistry = sessionRegistry;
        this.eventBus = eventBus;
        this.properties = properties;
    }

    public SessionRegistration register(String sessionId, DeliveryMode deliveryMode) {
        String effectiveSessionId = sessionId == null || sessionId.trim().isEmpty()
            ? UUID.randomUUID().toString()
            : sessionId;
        sessionRegistry.claim(effectiveSessionId);
        channels.computeIfAbsent(effectiveSessionId, this::newChannel);
        SessionRegistration registration = new SessionRegistration();
        registration.setSessionId(effectiveSessionId);
        registration.setNodeId(properties.getNodeId());
        registration.setDeliveryMode(deliveryMode);
        registration.setSseEndpoint("/api/sse/sessions/" + effectiveSessionId);
        registration.setWebSocketEndpoint("/ws/stream");
        return registration;
    }

    public Flux<ServerSentEvent<String>> subscribeSse(final String sessionId, final long lastSequence) {
        sessionRegistry.register(sessionId);
        SessionChannel channel = channels.computeIfAbsent(sessionId, this::newChannel);
        Flux<StreamEvent> replay = Flux.fromIterable(eventBus.loadHistory(sessionId, lastSequence));
        Flux<StreamEvent> live = channel.sink.asFlux().filter(event -> event.getSequence() > lastSequence);
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(15))
            .map(index -> ServerSentEvent.<String>builder().comment("keep-alive").build());

        return Flux.concat(replay, live)
            .distinct(StreamEvent::getEventId)
            .map(this::toSseEvent)
            .mergeWith(heartbeat)
            .doFinally(signalType -> sessionRegistry.release(sessionId));
    }

    public Flux<StreamEvent> subscribeWebSocket(final String sessionId, final long lastSequence) {
        sessionRegistry.register(sessionId);
        SessionChannel channel = channels.computeIfAbsent(sessionId, this::newChannel);
        Flux<StreamEvent> replay = Flux.fromIterable(eventBus.loadHistory(sessionId, lastSequence));
        Flux<StreamEvent> live = channel.sink.asFlux().filter(event -> event.getSequence() > lastSequence);
        return Flux.concat(replay, live)
            .distinct(StreamEvent::getEventId)
            .doFinally(signalType -> sessionRegistry.release(sessionId));
    }

    public void emit(StreamEvent event) {
        SessionChannel channel = channels.computeIfAbsent(event.getSessionId(), this::newChannel);
        channel.sink.emitNext(event, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(10)));
    }

    private SessionChannel newChannel(String sessionId) {
        return new SessionChannel(Sinks.many().replay().limit(properties.getSinkReplaySize()));
    }

    private ServerSentEvent<String> toSseEvent(StreamEvent event) {
        return ServerSentEvent.builder(event.getPayload())
            .id(String.valueOf(event.getSequence()))
            .event(event.getType().name().toLowerCase())
            .build();
    }

    private static final class SessionChannel {
        private final Sinks.Many<StreamEvent> sink;

        private SessionChannel(Sinks.Many<StreamEvent> sink) {
            this.sink = sink;
        }
    }
}
