package com.aistreaming.framework.service;

import com.aistreaming.framework.config.StreamingProperties;
import com.aistreaming.framework.domain.PromptTask;
import com.aistreaming.framework.support.JsonCodec;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Service
@ConditionalOnProperty(prefix = "ai.streaming.provider", name = "enabled", havingValue = "true")
public class OpenAiCompatibleAiStreamClient implements AiProviderStreamClient {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> EVENT_STREAM_TYPE =
        new ParameterizedTypeReference<ServerSentEvent<String>>() {
        };

    private final WebClient.Builder webClientBuilder;
    private final JsonCodec jsonCodec;
    private final StreamingProperties properties;

    public OpenAiCompatibleAiStreamClient(WebClient.Builder webClientBuilder,
                                          JsonCodec jsonCodec,
                                          StreamingProperties properties) {
        this.webClientBuilder = webClientBuilder;
        this.jsonCodec = jsonCodec;
        this.properties = properties;
    }

    @Override
    public Flux<String> stream(PromptTask task) {
        StreamingProperties.Provider provider = properties.getProvider();
        validateProvider(provider);

        return webClientBuilder
            .baseUrl(provider.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, bearerToken(provider.getApiKey()))
            .build()
            .post()
            .uri(provider.getChatPath())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(requestBody(task, provider))
            .retrieve()
            .bodyToFlux(EVENT_STREAM_TYPE)
            .concatMap(event -> decodeEvent(event.data()));
    }

    private Map<String, Object> requestBody(PromptTask task, StreamingProperties.Provider provider) {
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        if (StringUtils.hasText(provider.getSystemPrompt())) {
            messages.add(message("system", provider.getSystemPrompt()));
        }
        messages.add(message("user", task.getPrompt()));

        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("model", provider.getModel());
        request.put("messages", messages);
        request.put("stream", Boolean.TRUE);
        return request;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private Flux<String> decodeEvent(String data) {
        if (!StringUtils.hasText(data)) {
            return Flux.empty();
        }
        String trimmed = data.trim();
        if ("[DONE]".equals(trimmed)) {
            return Flux.empty();
        }

        OpenAiChunkResponse response = jsonCodec.fromJson(trimmed, OpenAiChunkResponse.class);
        List<String> tokens = new ArrayList<String>();
        if (response.getChoices() == null) {
            return Flux.empty();
        }
        for (OpenAiChoice choice : response.getChoices()) {
            if (choice == null || choice.getDelta() == null) {
                continue;
            }
            if (StringUtils.hasText(choice.getDelta().getContent())) {
                tokens.add(choice.getDelta().getContent());
            }
        }
        return Flux.fromIterable(tokens);
    }

    private void validateProvider(StreamingProperties.Provider provider) {
        if (!StringUtils.hasText(provider.getBaseUrl())) {
            throw new IllegalStateException("ai.streaming.provider.base-url must be configured");
        }
        if (!StringUtils.hasText(provider.getChatPath())) {
            throw new IllegalStateException("ai.streaming.provider.chat-path must be configured");
        }
        if (!StringUtils.hasText(provider.getApiKey())) {
            throw new IllegalStateException("ai.streaming.provider.api-key must be configured");
        }
        if (!StringUtils.hasText(provider.getModel())) {
            throw new IllegalStateException("ai.streaming.provider.model must be configured");
        }
    }

    private String bearerToken(String apiKey) {
        return "Bearer " + apiKey;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAiChunkResponse {

        private List<OpenAiChoice> choices;

        public List<OpenAiChoice> getChoices() {
            return choices;
        }

        public void setChoices(List<OpenAiChoice> choices) {
            this.choices = choices;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAiChoice {

        private OpenAiDelta delta;

        public OpenAiDelta getDelta() {
            return delta;
        }

        public void setDelta(OpenAiDelta delta) {
            this.delta = delta;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAiDelta {

        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}