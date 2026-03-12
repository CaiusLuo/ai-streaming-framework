package com.aistreaming.framework.service;

import com.aistreaming.framework.domain.PromptTask;
import reactor.core.publisher.Flux;

public interface AiProviderStreamClient {

    Flux<String> stream(PromptTask task);
}