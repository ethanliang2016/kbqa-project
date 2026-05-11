package com.kbqa.service;

import com.kbqa.model.ChatRequest;
import com.kbqa.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface ChatService {

    ChatResponse chat(ChatRequest request);

    Flux<ServerSentEvent<String>> chatStreamSSE(ChatRequest request);
}
