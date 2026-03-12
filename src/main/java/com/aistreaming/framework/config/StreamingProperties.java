package com.aistreaming.framework.config;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ai.streaming")
public class StreamingProperties {

    private String nodeId = "node-" + UUID.randomUUID().toString();
    private String workerStreamKey = "ai:worker:tasks";
    private String gatewayStreamPrefix = "ai:gateway:";
    private String sessionHistoryPrefix = "ai:history:";
    private String sessionRoutePrefix = "ai:route:";
    private String sessionSequencePrefix = "ai:seq:";
    private String gatewayGroupPrefix = "ai-gateway-";
    private String workerConsumerGroup = "ai-workers";
    private boolean messagingEnabled = true;
    private String messagingStreamPrefix = "ai:messaging:";
    private String messagingConsumerGroupPrefix = "ai:messaging-group:";
    private int historyMaxLength = 300;
    private int sinkReplaySize = 128;
    private int pendingWindowSize = 1024;
    private int sessionTtlSeconds = 180;
    private int pollCount = 50;
    private long pollTimeoutMillis = 1000L;
    private long routeRefreshMillis = 30000L;
    private long mockTokenDelayMillis = 50L;
    private Provider provider = new Provider();

    @Getter
    @Setter
    public static class Provider {

        private boolean enabled = false;
        private String baseUrl = "https://api.openai.com";
        private String chatPath = "/v1/chat/completions";
        private String apiKey = "";
        private String model = "gpt-4.1-mini";
        private String systemPrompt = "";
    }
}