package com.kbqa.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.kbqa.model.ChatRequest;
import com.kbqa.model.ChatResponse;
import com.kbqa.service.ChatService;
import com.kbqa.service.DocumentService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class ChatRagIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private DocumentService documentService;

    private static boolean documentsLoaded = false;

    @BeforeAll
    static void loadTestDocuments(@Autowired DocumentService documentService) {
        if (documentsLoaded) return;

        String content = """
                Java面试常见问题及答案：
                1. 什么是JVM？JVM是Java虚拟机，是运行Java字节码的运行时引擎。
                2. 什么是JDK？JDK是Java开发工具包，包含编译器和运行时环境。
                3. 什么是JRE？JRE是Java运行时环境，包含JVM和核心类库。
                4. Java的特点是什么？Java是面向对象、跨平台、安全、多线程的编程语言。
                5. 什么是垃圾回收？垃圾回收是JVM自动回收不再使用的对象所占内存的机制。
                6. HashMap的底层实现？JDK1.8中HashMap使用数组+链表+红黑树实现。
                7. 什么是Spring Boot？Spring Boot是简化Spring应用初始搭建和开发过程的框架。
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "java-interview-handbook.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));

        documentService.uploadDocument(file);
        documentsLoaded = true;
    }

    @Test
    void chat_aboutJvm() {
        ChatRequest request = new ChatRequest();
        request.setQuestion("什么是JVM？");

        ChatResponse response = chatService.chat(request);

        assertNotNull(response.getAnswer());
        assertFalse(response.getAnswer().isBlank());
        assertNotNull(response.getSources());
    }

    @Test
    void chat_aboutHashMap() {
        ChatRequest request = new ChatRequest();
        request.setQuestion("HashMap的底层实现是什么？");

        ChatResponse response = chatService.chat(request);

        assertNotNull(response.getAnswer());
        assertFalse(response.getAnswer().isBlank());
    }

    @Test
    void chat_aboutSpringBoot() {
        ChatRequest request = new ChatRequest();
        request.setQuestion("Spring Boot是什么？");

        ChatResponse response = chatService.chat(request);

        assertNotNull(response.getAnswer());
        assertFalse(response.getAnswer().isBlank());
    }

    @Test
    void chat_unknownTopic() {
        ChatRequest request = new ChatRequest();
        request.setQuestion("量子计算的基本原理是什么？");

        ChatResponse response = chatService.chat(request);

        assertNotNull(response.getAnswer());
        assertFalse(response.getAnswer().isBlank());
    }
}
