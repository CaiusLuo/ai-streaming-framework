package com.aistreaming.framework.domain;

public class SessionRegisterRequest {

    private String sessionId;
    private DeliveryMode deliveryMode = DeliveryMode.SSE;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }
}
