package com.kbqa.unit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbqa.controller.ChatController;
import com.kbqa.exception.GlobalExceptionHandler;
import com.kbqa.model.ChatRequest;
import com.kbqa.model.ChatResponse;
import com.kbqa.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

@WebMvcTest(ChatController.class)
@ContextConfiguration(classes = {ChatController.class, GlobalExceptionHandler.class})
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @Test
    void chat_success() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setQuestion("什么是Java？");

        ChatResponse response = ChatResponse.builder()
                .answer("Java是一种面向对象的编程语言。")
                .sources(java.util.Collections.emptyList())
                .build();

        when(chatService.chat(any())).thenReturn(response);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Java是一种面向对象的编程语言。"));
    }

    @Test
    void chat_emptyQuestion() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setQuestion("");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_nullQuestion() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatStream_success() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setQuestion("测试流式问答");

        ServerSentEvent<String> sourceEvent = ServerSentEvent
                .<String>builder().event("sources").data("[]").build();
        ServerSentEvent<String> tokenEvent = ServerSentEvent
                .<String>builder().event("token").data("测试").build();
        ServerSentEvent<String> doneEvent = ServerSentEvent
                .<String>builder().event("done").data("[DONE]").build();

        when(chatService.chatStreamSSE(any()))
                .thenReturn(Flux.just(sourceEvent, tokenEvent, doneEvent));

        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
