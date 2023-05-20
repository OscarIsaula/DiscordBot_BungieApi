package com.example.discord4j.Controllers;

import com.example.discord4j.Listeners.ConcreteMessageListener;
import com.example.discord4j.Models.Raid;
import discord4j.core.object.entity.Message;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

@Service
public class LowManService {
    @Value("${bungie.api.key}")
    private String apiKey;
    private final WebClient webClient;
    private final ConcreteMessageListener listener;
    private final Map<String, Raid> raids = Raid.getRaids(); // Call HashMap in Models.Raid
    private HashSet<String> report = new HashSet<>();

    @Autowired
    protected LowManService(WebClient webClient) {
        this.webClient = webClient;
        this.listener = new ConcreteMessageListener();
    }
    protected void getActivityHistory(List<String> characterIds, String membershipType,
                                      String membershipId, Message message) {
        String activityHistoryPath = "/Destiny2/{membershipType}/Account/{membershipId}" +
                "/Character/{characterId}/Stats/Activities/";

        Flux.range(0, 50) // Create a Flux for the pages
                .concatMap(page ->
                        Flux.fromIterable(characterIds) // Create a Flux for the characterIds
                                .concatMap(characterId -> {
                                    String pageNum = String.valueOf(page);

                                    return webClient.get()
                                            .uri(uriBuilder -> uriBuilder
                                                    .path(activityHistoryPath)
                                                    .queryParam("mode", "4") // 4 for raids
                                                    .queryParam("count", "100")
                                                    .queryParam("page", pageNum)
                                                    .build(membershipType, membershipId, characterId))
                                            .retrieve()
                                            .bodyToMono(String.class);
                                })
                )
                .delayElements(Duration.ofSeconds(2)) // Add a delay of 1 second between each request
                .subscribe(
                        responseBody -> parseActivityHistory(responseBody, message),
                        error -> System.err.println("Error accessing Bungie API: " + error.getMessage())
                );
    }

    private void parseActivityHistory(String responseBody, Message message) {
        JSONObject jsonObject = new JSONObject(responseBody);
        JSONObject responseData = jsonObject.getJSONObject("Response");
        if (!responseData.has("activities")) {
            return;
        }

        List<Raid> raidList = Arrays.asList(raids.get("LW"), raids.get("GoS"), raids.get("DSC"), raids.get("VoG"),
                raids.get("VotD"), raids.get("KF"), raids.get("RoN"));

        JSONArray activities = responseData.getJSONArray("activities");

        for (int i = 0; i < activities.length(); i++) {
            JSONObject activity = activities.getJSONObject(i);
            long activityHash = activity.getJSONObject("activityDetails").getLong("referenceId");

            raidList.stream()
                    .filter(raid -> raid != null && Long.toString(activityHash).equals(raid.hash()))
                    .findFirst()
                    .ifPresent(raid -> getInstanceIds
                            (activity, raid, message));
        }
    }
    private void getInstanceIds(JSONObject activity, Raid raid, Message message) {
        JSONObject values = activity.getJSONObject("values");
        double completedValue = values.getJSONObject("completed").getJSONObject("basic").getDouble("value");
        double playerCount = values.getJSONObject("playerCount").getJSONObject("basic").getDouble("value");

        if (completedValue == 1.0 && playerCount <= 9.0) {
            long instanceId = activity.getJSONObject("activityDetails").getLong("instanceId");
            pgcrEndpoint(String.valueOf(instanceId), raid, message);
        }
    }
    private void pgcrEndpoint(String instanceId, Raid raid, Message message) {
        String pgcrEndpoint = "/Destiny2/Stats/PostGameCarnageReport/{instanceId}/";

        WebClient statsWebClient = WebClient.builder()
                .baseUrl("https://stats.bungie.net/Platform")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-API-Key", apiKey)
                .build();

        Mono<String> response = statsWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(pgcrEndpoint)
                        .build(instanceId))
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))); // Retry 3 times with an exponential backoff starting at 1 second

        response.subscribe(
                responseBody -> isLowMan(responseBody, raid, instanceId, message),
                error -> System.err.println("Error accessing Bungie API: " + error.getMessage())
        );
    }

    private void isLowMan(String responseBody, Raid raid, String instanceId, Message message) {
        JSONObject jsonObject = new JSONObject(responseBody);
        JSONObject responseData = jsonObject.getJSONObject("Response");

        HashSet<String> accountCount = new HashSet<>();
        double totalDeaths = 0.0;
        JSONArray entries = responseData.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String bungieGlobalDisplayName = entry.getJSONObject("player").getJSONObject
                    ("destinyUserInfo").getString("bungieGlobalDisplayName");
            double playerDeaths = entry.getJSONObject("values").getJSONObject("deaths")
                    .getJSONObject("basic").getDouble("value");
            totalDeaths += playerDeaths;
            accountCount.add(bungieGlobalDisplayName);
        }
        if (accountCount.size() <= 3) {
            int playerCount = accountCount.size();
            raidTags(playerCount, responseData, totalDeaths, raid, instanceId, message);
        }
    }
    private void raidTags(int playerCount, JSONObject responseData, double totalDeaths, Raid raid,
                          String instanceId, Message message) {
        String lowmanCategory = getLowmanCategory(playerCount);
        String flawlessStatus = isFlawless(totalDeaths);
        String freshness = isFresh(responseData);

        String entry = lowmanCategory + flawlessStatus + freshness + raid.name();
        addToReport(entry, instanceId, message);
    }
    private String getLowmanCategory(int playerCount) {
        return switch (playerCount) {
            case 2 -> "Duo ";
            case 3 -> "Trio ";
            default -> "Solo ";
        };
    }

    private String isFlawless(double totalDeaths) {
        return totalDeaths == 0 ? "Flawless " : "";
    }

    private String isFresh(JSONObject responseData) {
        boolean isFresh = responseData.getBoolean("activityWasStartedFromBeginning");
        return isFresh ? "Fresh " : "Checkpoint ";
    }
    private void addToReport(String entry, String instanceId, Message message) {
        if (!report.contains(entry)) {
            report.add(entry);
            String raidReport = " <https://raid.report/pgcr/" + instanceId + ">";
            String finalEntry = entry + raidReport;

            listener.lowmanCommand(finalEntry, message).subscribe();
            System.out.println(finalEntry);
        }
    }
}