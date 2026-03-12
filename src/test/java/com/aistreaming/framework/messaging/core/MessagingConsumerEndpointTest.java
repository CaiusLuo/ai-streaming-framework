package com.aistreaming.framework.messaging.core;

import com.aistreaming.framework.support.JsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MessagingConsumerEndpointTest {

    private final JsonCodec jsonCodec = new JsonCodec(new ObjectMapper());

    @Test
    void shouldDeserializePayloadAndInvokeConsumerMethod() throws Exception {
        DemoConsumer consumer = new DemoConsumer();
        MessagingConsumerEndpoint endpoint = MessagingConsumerEndpoint.create(
            consumer,
            DemoConsumer.class.getMethod("handle", SamplePayload.class),
            "demoService",
            "demo.binding",
            "demo.group",
            "demo-consumer"
        );

        MessagingRecord record = new MessagingRecord();
        record.setPayload("{\"value\":\"hello\"}");
        endpoint.invoke(record, jsonCodec);

        Assertions.assertNotNull(consumer.payload);
        Assertions.assertEquals("hello", consumer.payload.getValue());
    }

    @Test
    void shouldPassMessagingRecordDirectlyWhenRequested() throws Exception {
        DemoRecordConsumer consumer = new DemoRecordConsumer();
        MessagingConsumerEndpoint endpoint = MessagingConsumerEndpoint.create(
            consumer,
            DemoRecordConsumer.class.getMethod("handle", MessagingRecord.class),
            "demoService",
            "demo.binding",
            "demo.group",
            "demo-consumer"
        );

        MessagingRecord record = new MessagingRecord();
        record.setMessageId("msg-1");
        endpoint.invoke(record, jsonCodec);

        Assertions.assertSame(record, consumer.record);
    }

    static class DemoConsumer {

        private SamplePayload payload;

        public void handle(SamplePayload payload) {
            this.payload = payload;
        }
    }

    static class DemoRecordConsumer {

        private MessagingRecord record;

        public void handle(MessagingRecord record) {
            this.record = record;
        }
    }

    static class SamplePayload {

        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}