package com.aistreaming.framework.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PromptTask {

    private String requestId;
    private String sessionId;
    private String prompt;
    private String routeNodeId;
    private long createdAt;
}