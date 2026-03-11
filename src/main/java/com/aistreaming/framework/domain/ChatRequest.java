package com.aistreaming.framework.domain;

import javax.validation.constraints.NotBlank;

public class ChatRequest {

    @NotBlank
    private String sessionId;

    @NotBlank
    private String prompt;

    private String requestId;

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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
