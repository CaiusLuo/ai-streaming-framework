package com.aistreaming.framework.config;

import com.aistreaming.framework.gateway.StreamWebSocketHandler;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketHandlerMapping(StreamWebSocketHandler handler) {
        Map<String, WebSocketHandler> urlMap = new HashMap<String, WebSocketHandler>();
        urlMap.put("/ws/stream", handler);
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(-1);
        mapping.setUrlMap(urlMap);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
