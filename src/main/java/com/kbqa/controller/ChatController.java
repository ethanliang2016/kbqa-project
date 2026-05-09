package com.kbqa.controller;

import com.kbqa.model.ChatRequest;
import com.kbqa.model.ChatResponse;
import com.kbqa.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("[API] Chat request received: questionLength={}chars", request.getQuestion().length());
        ChatResponse response = chatService.chat(request);
        log.info("[API] Chat response sent: sources={}, answerLength={}chars",
                response.getSources().size(), response.getAnswer().length());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stream")
    public ResponseEntity<ChatResponse> chatStream(@Valid @RequestBody ChatRequest request) {
        log.info("[API] Stream chat request received: questionLength={}chars", request.getQuestion().length());
        ChatResponse response = chatService.chatStream(request);
        log.info("[API] Stream chat response sent: sources={}, answerLength={}chars",
                response.getSources().size(), response.getAnswer().length());
        return ResponseEntity.ok(response);
    }
}
