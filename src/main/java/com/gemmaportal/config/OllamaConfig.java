package com.gemmaportal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
public class    OllamaConfig {

    @Bean
    public RestClient ollamaRestClient(OllamaProperties props) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(2));

        ReactorClientHttpRequestFactory factory = new ReactorClientHttpRequestFactory(httpClient);

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}