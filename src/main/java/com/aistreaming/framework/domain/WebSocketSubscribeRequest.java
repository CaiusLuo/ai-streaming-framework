package com.aistreaming.framework.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebSocketSubscribeRequest {

    private String action = "subscribe";
    private String sessionId;
    private Long lastSequence;
}