package com.aistreaming.framework.domain;

public class PromptTask {

    private String requestId;
    private String sessionId;
    private String prompt;
    private String routeNodeId;
    private long createdAt;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getRouteNodeId() {
        return routeNodeId;
    }

    public void setRouteNodeId(String routeNodeId) {
        this.routeNodeId = routeNodeId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
