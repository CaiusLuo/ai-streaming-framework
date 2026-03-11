package com.aistreaming.framework.gateway;

import com.aistreaming.framework.domain.StreamEvent;
import com.aistreaming.framework.domain.WebSocketSubscribeRequest;
import com.aistreaming.framework.service.SessionSinkHub;
import com.aistreaming.framework.support.JsonCodec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

@Component
public class StreamWebSocketHandler implements WebSocketHandler {

    private final SessionSinkHub sessionSinkHub;
    private final JsonCodec jsonCodec;

    public StreamWebSocketHandler(SessionSinkHub sessionSinkHub, JsonCodec jsonCodec) {
        this.sessionSinkHub = sessionSinkHub;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public Mono<Void> handle(final WebSocketSession session) {
        Mono<WebSocketSubscribeRequest> subscribeCommand = session.receive()
            .next()
            .map(WebSocketMessage::getPayloadAsText)
            .map(payload -> jsonCodec.fromJson(payload, WebSocketSubscribeRequest.class));

        return subscribeCommand.flatMap(command -> {
            long lastSequence = command.getLastSequence() == null ? 0L : command.getLastSequence().longValue();
            Flux<String> outbound = sessionSinkHub.subscribeWebSocket(command.getSessionId(), lastSequence)
                .map(this::serialize);
            return session.send(outbound.map(session::textMessage));
        });
    }

    private String serialize(StreamEvent event) {
        return jsonCodec.toJson(event);
    }
}
