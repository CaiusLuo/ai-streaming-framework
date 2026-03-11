package com.aistreaming.framework.gateway;

import com.aistreaming.framework.domain.SessionRegisterRequest;
import com.aistreaming.framework.domain.SessionRegistration;
import com.aistreaming.framework.service.SessionSinkHub;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionSinkHub sessionSinkHub;

    public SessionController(SessionSinkHub sessionSinkHub) {
        this.sessionSinkHub = sessionSinkHub;
    }

    @PostMapping("/sessions/register")
    public SessionRegistration register(@Valid @RequestBody SessionRegisterRequest request) {
        return sessionSinkHub.register(request.getSessionId(), request.getDeliveryMode());
    }

    @GetMapping(value = "/sse/sessions/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@PathVariable("sessionId") String sessionId,
                                                @RequestParam(value = "lastSequence", required = false) Long lastSequence,
                                                @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return sessionSinkHub.subscribeSse(sessionId, resolveLastSequence(lastSequence, lastEventId));
    }

    private long resolveLastSequence(Long lastSequence, String lastEventId) {
        if (lastSequence != null) {
            return lastSequence.longValue();
        }
        if (lastEventId == null || lastEventId.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(lastEventId);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
