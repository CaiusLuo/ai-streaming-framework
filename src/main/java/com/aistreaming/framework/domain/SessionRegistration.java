package com.aistreaming.framework.domain;

public class SessionRegistration {

    private String sessionId;
    private String nodeId;
    private DeliveryMode deliveryMode;
    private String sseEndpoint;
    private String webSocketEndpoint;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getSseEndpoint() {
        return sseEndpoint;
    }

    public void setSseEndpoint(String sseEndpoint) {
        this.sseEndpoint = sseEndpoint;
    }

    public String getWebSocketEndpoint() {
        return webSocketEndpoint;
    }

    public void setWebSocketEndpoint(String webSocketEndpoint) {
        this.webSocketEndpoint = webSocketEndpoint;
    }
}
