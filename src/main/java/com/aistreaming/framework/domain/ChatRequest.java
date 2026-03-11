package com.aistreaming.framework.domain;

import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {

    @NotBlank
    private String sessionId;

    @NotBlank
    private String prompt;

    private String requestId;
}