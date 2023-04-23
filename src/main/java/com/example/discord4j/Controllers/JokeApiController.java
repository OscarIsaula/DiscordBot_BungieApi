package com.example.discord4j.Controllers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class JokeApiController {
    private static final String JOKE_API_BASE_URL = "https://v2.jokeapi.dev/joke/";

    public Mono<String> getRandomJoke() {
        String categories = "Programming,Miscellaneous,Christmas";
        String apiUrl = JOKE_API_BASE_URL + categories;

        WebClient webClient = WebClient.builder()
                .baseUrl(JOKE_API_BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE)
                .build();

        return webClient.get()
                .uri(apiUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseApiResponse)
                .onErrorReturn("Error: " +
                        "Unable to fetch a joke from the JokeAPI.");
    }

    private String parseApiResponse(String responseBody) {
        org.json.JSONObject jsonObject = new org.json.JSONObject(responseBody);

        if (jsonObject.getString("type")
                .equalsIgnoreCase("single")) {
            return jsonObject.getString("joke");
        } else if (jsonObject.getString("type")
                .equalsIgnoreCase("twopart")) {
            return jsonObject.getString("setup") + "\n" +
                    jsonObject.getString("delivery");
        } else {
            return "Error: Unable to parse the joke from the JokeAPI response.";
        }
    }
}