package com.gemmaportal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    private String baseUrl = "http://localhost:11434";
    private String model = "gemma3";
    private Duration streamTimeout = Duration.ofSeconds(120);
    private Double temperature = 0.1;

}
