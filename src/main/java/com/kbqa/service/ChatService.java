package com.kbqa.service;

import com.kbqa.model.ChatRequest;
import com.kbqa.model.ChatResponse;

public interface ChatService {

    ChatResponse chat(ChatRequest request);

    ChatResponse chatStream(ChatRequest request);
}
