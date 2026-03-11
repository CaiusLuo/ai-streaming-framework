package com.aistreaming.framework.service;

import com.aistreaming.framework.config.StreamingProperties;
import com.aistreaming.framework.domain.StreamEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OrderedEventProcessorTest {

    @Test
    void shouldDeliverEventsInOrderAndIgnoreDuplicateEventIds() {
        OrderedEventProcessor processor = new OrderedEventProcessor(new StreamingProperties());
        List<Long> delivered = new ArrayList<Long>();

        processor.process(event("session-1", 2L, "evt-2"), e -> delivered.add(e.getSequence()));
        processor.process(event("session-1", 1L, "evt-1"), e -> delivered.add(e.getSequence()));
        processor.process(event("session-1", 2L, "evt-2"), e -> delivered.add(e.getSequence()));
        processor.process(event("session-1", 3L, "evt-3"), e -> delivered.add(e.getSequence()));

        Assertions.assertEquals(3, delivered.size());
        Assertions.assertEquals(Long.valueOf(1L), delivered.get(0));
        Assertions.assertEquals(Long.valueOf(2L), delivered.get(1));
        Assertions.assertEquals(Long.valueOf(3L), delivered.get(2));
    }

    private StreamEvent event(String sessionId, long sequence, String eventId) {
        StreamEvent event = new StreamEvent();
        event.setSessionId(sessionId);
        event.setSequence(sequence);
        event.setEventId(eventId);
        return event;
    }
}