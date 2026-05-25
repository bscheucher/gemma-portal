package com.gemmaportal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    private String baseUrl = "http://localhost:11434";
    private String model = "gemma3";

}
