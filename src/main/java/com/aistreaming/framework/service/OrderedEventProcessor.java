package com.aistreaming.framework.service;

import com.aistreaming.framework.config.StreamingProperties;
import com.aistreaming.framework.domain.StreamEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class OrderedEventProcessor {

    private final Map<String, SessionWindow> windows = new ConcurrentHashMap<String, SessionWindow>();
    private final StreamingProperties properties;

    public OrderedEventProcessor(StreamingProperties properties) {
        this.properties = properties;
    }

    public void process(StreamEvent event, Consumer<StreamEvent> consumer) {
        SessionWindow window = windows.computeIfAbsent(event.getSessionId(), key -> new SessionWindow());
        synchronized (window.monitor) {
            if (window.seen(event.getEventId())) {
                return;
            }
            if (event.getSequence() <= 0) {
                window.remember(event.getEventId());
                consumer.accept(event);
                return;
            }
            if (window.nextExpectedSequence == 1L && window.pending.isEmpty() && event.getSequence() > 1L) {
                window.nextExpectedSequence = event.getSequence();
            }
            if (event.getSequence() < window.nextExpectedSequence) {
                window.remember(event.getEventId());
                return;
            }
            if (event.getSequence() > window.nextExpectedSequence) {
                if (window.pending.size() >= properties.getPendingWindowSize() && !window.pending.isEmpty()) {
                    Long firstKey = window.pending.keySet().iterator().next();
                    window.pending.remove(firstKey);
                }
                window.pending.put(event.getSequence(), event);
                return;
            }
            deliver(window, event, consumer);
            while (true) {
                StreamEvent next = window.pending.remove(window.nextExpectedSequence);
                if (next == null) {
                    break;
                }
                deliver(window, next, consumer);
            }
        }
    }

    private void deliver(SessionWindow window, StreamEvent event, Consumer<StreamEvent> consumer) {
        window.remember(event.getEventId());
        window.nextExpectedSequence = event.getSequence() + 1;
        consumer.accept(event);
    }

    private static final class SessionWindow {
        private final Object monitor = new Object();
        private final Map<Long, StreamEvent> pending = new LinkedHashMap<Long, StreamEvent>();
        private final Map<String, Boolean> recentEventIds = new LinkedHashMap<String, Boolean>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > 2048;
            }
        };
        private long nextExpectedSequence = 1L;

        private boolean seen(String eventId) {
            return recentEventIds.containsKey(eventId);
        }

        private void remember(String eventId) {
            recentEventIds.put(eventId, Boolean.TRUE);
        }
    }
}
