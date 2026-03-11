package com.aistreaming.framework.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionRegistration {

    private String sessionId;
    private String nodeId;
    private DeliveryMode deliveryMode;
    private String sseEndpoint;
    private String webSocketEndpoint;
}