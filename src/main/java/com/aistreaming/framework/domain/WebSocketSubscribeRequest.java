package com.aistreaming.framework.domain;

public class WebSocketSubscribeRequest {

    private String action = "subscribe";
    private String sessionId;
    private Long lastSequence;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(Long lastSequence) {
        this.lastSequence = lastSequence;
    }
}
