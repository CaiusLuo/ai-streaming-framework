package com.aistreaming.framework.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonCodec {

    private final ObjectMapper objectMapper;

    public JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize object", ex);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize json", ex);
        }
    }
}
