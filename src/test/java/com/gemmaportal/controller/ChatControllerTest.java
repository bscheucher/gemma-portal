package com.gemmaportal.controller;

import com.gemmaportal.dto.ChatMessage;
import com.gemmaportal.dto.ChatResponse;
import com.gemmaportal.service.OllamaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OllamaService ollamaService;

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/chat/health"))
                .andExpect(status().isOk());
    }

    @Test
    void chatRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatRejectsWhitespaceMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatRejectsNullMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatReturnsResponse() throws Exception {
        when(ollamaService.chat(eq("hello"), any()))
                .thenReturn(new ChatResponse("hi there", "gemma4"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("hi there"))
                .andExpect(jsonPath("$.model").value("gemma4"));
    }

    @Test
    void getHistoryReturnsMessages() throws Exception {
        when(ollamaService.getConversationHistory(any()))
                .thenReturn(List.of(
                        new ChatMessage("user", "hello"),
                        new ChatMessage("assistant", "hi")));

        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("hello"))
                .andExpect(jsonPath("$[1].role").value("assistant"));
    }

    @Test
    void clearHistoryDelegatesToService() throws Exception {
        mockMvc.perform(delete("/api/chat/history"))
                .andExpect(status().isOk());

        verify(ollamaService).clearHistory(any());
    }
}