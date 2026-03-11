package com.aistreaming.framework.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StreamEvent {

    private String eventId;
    private String requestId;
    private String sessionId;
    private String routeNodeId;
    private long sequence;
    private MessageType type;
    private String payload;
    private boolean terminal;
    private long createdAt;
}