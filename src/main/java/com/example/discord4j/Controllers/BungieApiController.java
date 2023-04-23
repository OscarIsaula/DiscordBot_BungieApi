package com.example.discord4j.Controllers;

import discord4j.core.object.entity.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@RestController
public class BungieApiController {
    private final WebClient bungieWebClient;
    private final DayOneService dayOneController;
    private final LowManService lowManController;

    @Autowired
    public BungieApiController(WebClient bungieWebClient,
                               DayOneService dayOneController,
                               LowManService lowManController) {
        this.bungieWebClient = bungieWebClient;
        this.dayOneController = dayOneController;
        this.lowManController = lowManController;
    }
    public void getMembershipInfo(String bungieId, String command, Message message) {
        String searchDestinyPlayer = "/Destiny2/SearchDestinyPlayer/-1/{bungieId}/";
        System.out.println("Bungie ID: " + bungieId);

        Mono<String> response = bungieWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(searchDestinyPlayer)
                        .build(bungieId))
                .retrieve()
                .bodyToMono(String.class);

        response.subscribe(
                responseBody -> parseMembershipInfo(responseBody, command, message),
                error -> System.err.println("Error accessing Bungie API: " + error.getMessage())
        );
    }

    private void parseMembershipInfo (String responseBody, String command, Message message) {
        String membershipType, membershipId;
        JSONObject jsonObject = new JSONObject(responseBody);
        JSONArray responseArray = jsonObject.getJSONArray("Response");
        JSONObject user = responseArray.getJSONObject(0);

        membershipType = String.valueOf(user.getInt("membershipType"));
        membershipId = user.getString("membershipId");
        getProfileData(membershipType, membershipId, command, message);
    }
    private void getProfileData(String membershipType, String membershipId,
                                String command, Message message) {
        String searchProfile = "/Destiny2/{membershipType}/Profile/{membershipId}/";

        Mono<String> response = bungieWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(searchProfile)
                        .queryParam("components", "200")
                        .build(membershipType, membershipId))
                .retrieve()
                .bodyToMono(String.class);

        response.subscribe(
                responseBody -> parseProfileData
                        (responseBody, membershipType, membershipId, command, message),
                error -> System.err.println
                        ("Error accessing Bungie API: " + error.getMessage())
        );
    }
    private void parseProfileData(String responseBody, String membershipType,
                                  String membershipId, String command, Message message) {

        JSONObject jsonObject = new JSONObject(responseBody);
        JSONObject responseData = jsonObject.getJSONObject("Response");
        JSONObject charactersData = responseData.getJSONObject
                ("characters").getJSONObject("data");

        System.out.println("Membership Type: " + membershipType);
        System.out.println("Membership ID: " + membershipId);
        List<String> characterIds = new ArrayList<>();
        for (String characterId : charactersData.keySet()) {
            characterIds.add(characterId);
            System.out.println("CharacterId: " + characterId);
        }
        if (command.equals("day1")) {
            dayOneController.getActivityHistory
                    (characterIds, membershipType, membershipId, message);
        }
        else if (command.equals("lowman")) {
            lowManController.getActivityHistory
                    (characterIds, membershipType, membershipId, message);
        }
    }
}