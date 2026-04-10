package com.gemmaportal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaChatRequest {
    private String model;
    private List<ChatMessage> messages;
    private boolean stream;
}
