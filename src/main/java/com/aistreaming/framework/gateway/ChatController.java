package com.aistreaming.framework.gateway;

import com.aistreaming.framework.domain.ChatRequest;
import com.aistreaming.framework.service.ChatCommandService;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatCommandService chatCommandService;

    public ChatController(ChatCommandService chatCommandService) {
        this.chatCommandService = chatCommandService;
    }

    @PostMapping("/requests")
    public Map<String, String> submit(@Valid @RequestBody ChatRequest request) {
        return chatCommandService.submit(request);
    }
}
