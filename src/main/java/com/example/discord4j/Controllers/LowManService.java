package com.example.discord4j.Controllers;

import com.example.discord4j.Models.Raid;
import discord4j.core.object.entity.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class LowManService {
    private final WebClient webClient;
    private final Map<String, Raid> raids = Raid.getRaids(); // Call HashMap in Models.Raid

    @Autowired
    public LowManService(WebClient webClient) {
        this.webClient = webClient;
    }
    public void getActivityHistory(List<String> characterIds, String membershipType,
                                   String membershipId, Message message) {
        String activityHistoryPath = "/Destiny2/{membershipType}/Account/{membershipId}" +
                "/Character/{characterId}/Stats/Activities/";

        for (String characterId : characterIds) {

            for (int page = 0; page < 50; page++) {
                String pageNum = String.valueOf(page);

                Mono<String> response = webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(activityHistoryPath)
                                .queryParam("mode", "4") // 4 for raids
                                .queryParam("count", "100")
                                .queryParam("page", pageNum)
                                .build(membershipType, membershipId, characterId))
                        .retrieve()
                        .bodyToMono(String.class);

                response.subscribe(
                        responseBody -> parseActivityHistory
                                (responseBody, message),
                        error -> System.err.println
                                ("Error accessing Bungie API: " + error.getMessage())
                );
            }
        }
    }
    public void parseActivityHistory(String responseBody, Message message) {

    }
}