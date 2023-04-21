package com.example.discord4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfiguration {

    @Value("${bungie.api.key}")
    private String apiKey;

    private static final String BUNGIE_API_BASE_URL =
            "https://www.bungie.net/Platform";

    @Bean
    public WebClient bungieWebClient() {
        return WebClient.builder()
                .baseUrl(BUNGIE_API_BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }
}
