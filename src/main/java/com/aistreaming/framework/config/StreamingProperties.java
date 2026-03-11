package com.aistreaming.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.streaming")
public class StreamingProperties {

    private String nodeId;
    private String workerStreamKey = "ai:worker:tasks";
    private String gatewayStreamPrefix = "ai:gateway:";
    private String sessionHistoryPrefix = "ai:history:";
    private String sessionRoutePrefix = "ai:route:";
    private String sessionSequencePrefix = "ai:seq:";
    private String gatewayGroupPrefix = "ai-gateway-";
    private String workerConsumerGroup = "ai-workers";
    private int historyMaxLength = 300;
    private int sinkReplaySize = 128;
    private int pendingWindowSize = 1024;
    private int sessionTtlSeconds = 180;
    private int pollCount = 50;
    private long pollTimeoutMillis = 1000L;
    private long routeRefreshMillis = 30000L;
    private long mockTokenDelayMillis = 50L;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getWorkerStreamKey() {
        return workerStreamKey;
    }

    public void setWorkerStreamKey(String workerStreamKey) {
        this.workerStreamKey = workerStreamKey;
    }

    public String getGatewayStreamPrefix() {
        return gatewayStreamPrefix;
    }

    public void setGatewayStreamPrefix(String gatewayStreamPrefix) {
        this.gatewayStreamPrefix = gatewayStreamPrefix;
    }

    public String getSessionHistoryPrefix() {
        return sessionHistoryPrefix;
    }

    public void setSessionHistoryPrefix(String sessionHistoryPrefix) {
        this.sessionHistoryPrefix = sessionHistoryPrefix;
    }

    public String getSessionRoutePrefix() {
        return sessionRoutePrefix;
    }

    public void setSessionRoutePrefix(String sessionRoutePrefix) {
        this.sessionRoutePrefix = sessionRoutePrefix;
    }

    public String getSessionSequencePrefix() {
        return sessionSequencePrefix;
    }

    public void setSessionSequencePrefix(String sessionSequencePrefix) {
        this.sessionSequencePrefix = sessionSequencePrefix;
    }

    public String getGatewayGroupPrefix() {
        return gatewayGroupPrefix;
    }

    public void setGatewayGroupPrefix(String gatewayGroupPrefix) {
        this.gatewayGroupPrefix = gatewayGroupPrefix;
    }

    public String getWorkerConsumerGroup() {
        return workerConsumerGroup;
    }

    public void setWorkerConsumerGroup(String workerConsumerGroup) {
        this.workerConsumerGroup = workerConsumerGroup;
    }

    public int getHistoryMaxLength() {
        return historyMaxLength;
    }

    public void setHistoryMaxLength(int historyMaxLength) {
        this.historyMaxLength = historyMaxLength;
    }

    public int getSinkReplaySize() {
        return sinkReplaySize;
    }

    public void setSinkReplaySize(int sinkReplaySize) {
        this.sinkReplaySize = sinkReplaySize;
    }

    public int getPendingWindowSize() {
        return pendingWindowSize;
    }

    public void setPendingWindowSize(int pendingWindowSize) {
        this.pendingWindowSize = pendingWindowSize;
    }

    public int getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public void setSessionTtlSeconds(int sessionTtlSeconds) {
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    public int getPollCount() {
        return pollCount;
    }

    public void setPollCount(int pollCount) {
        this.pollCount = pollCount;
    }

    public long getPollTimeoutMillis() {
        return pollTimeoutMillis;
    }

    public void setPollTimeoutMillis(long pollTimeoutMillis) {
        this.pollTimeoutMillis = pollTimeoutMillis;
    }

    public long getRouteRefreshMillis() {
        return routeRefreshMillis;
    }

    public void setRouteRefreshMillis(long routeRefreshMillis) {
        this.routeRefreshMillis = routeRefreshMillis;
    }

    public long getMockTokenDelayMillis() {
        return mockTokenDelayMillis;
    }

    public void setMockTokenDelayMillis(long mockTokenDelayMillis) {
        this.mockTokenDelayMillis = mockTokenDelayMillis;
    }
}
