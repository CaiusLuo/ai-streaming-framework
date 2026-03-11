package com.aistreaming.framework.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SessionRegisterRequest {

    private String sessionId;
    private DeliveryMode deliveryMode = DeliveryMode.SSE;
}