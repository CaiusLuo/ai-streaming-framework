package com.aistreaming.framework.messaging.core;

public class MessagePublishResult {

    private final String messageId;
    private final String binding;
    private final String recordId;

    public MessagePublishResult(String messageId, String binding, String recordId) {
        this.messageId = messageId;
        this.binding = binding;
        this.recordId = recordId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getBinding() {
        return binding;
    }

    public String getRecordId() {
        return recordId;
    }
}
