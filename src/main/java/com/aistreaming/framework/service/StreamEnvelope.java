package com.aistreaming.framework.service;

import org.springframework.data.redis.connection.stream.RecordId;

public class StreamEnvelope<T> {

    private final String streamKey;
    private final String group;
    private final RecordId recordId;
    private final T payload;

    public StreamEnvelope(String streamKey, String group, RecordId recordId, T payload) {
        this.streamKey = streamKey;
        this.group = group;
        this.recordId = recordId;
        this.payload = payload;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public String getGroup() {
        return group;
    }

    public RecordId getRecordId() {
        return recordId;
    }

    public T getPayload() {
        return payload;
    }
}