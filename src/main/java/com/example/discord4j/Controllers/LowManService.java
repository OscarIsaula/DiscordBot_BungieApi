package com.example.discord4j.Controllers;

import com.example.discord4j.Models.Raid;
import discord4j.core.object.entity.Message;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class LowManService {
    private final WebClient webClient;
    private final Map<String, Raid> raids = Raid.getRaids(); // Call HashMap in Models.Raid
    List<Raid> raidList = Arrays.asList(raids.get("LW"), raids.get("SotP"),
            raids.get("CoS"), raids.get("GoS"), raids.get("DSC"), raids.get("VoG"),
            raids.get("VotD"), raids.get("KF"), raids.get("RoN"));
    @Autowired
    protected LowManService(WebClient webClient) {
        this.webClient = webClient;
    }
    protected void getActivityHistory(List<String> characterIds, String membershipType,
                                   String membershipId, Message message) {
        String activityHistoryPath = "/Destiny2/{membershipType}/Account/{membershipId}" +
                "/Character/{characterId}/Stats/Activities/";

        for (int page = 0; page < 50; page++) {
            for (String characterId : characterIds) {
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
    private void parseActivityHistory(String responseBody, Message message) {
        Map<String, String> soloInstanceIds = new HashMap<>();
        Map<String, String> duoInstanceIds = new HashMap<>();
        Map<String, String> trioInstanceIds = new HashMap<>();
        JSONObject jsonObject = new JSONObject(responseBody);
        JSONObject responseData = jsonObject.getJSONObject("Response");
        if (!responseData.has("activities")) {
            return;
        }
        JSONArray activities = responseData.getJSONArray("activities");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (int i = 0; i < activities.length(); i++) {
            JSONObject activity = activities.getJSONObject(i);
            long activityHash = activity.getJSONObject("activityDetails").getLong("referenceId");

            raidList.stream()
                    .filter(raid -> raid != null && Long.toString(activityHash).equals(raid.hash()))
                    .findFirst()
                    .ifPresent(raid -> getInstanceIds
                            (activity, raid, soloInstanceIds, duoInstanceIds, trioInstanceIds, formatter));
        }
        processRaids(soloInstanceIds, duoInstanceIds, trioInstanceIds);
    }
    private void getInstanceIds(JSONObject activity, Raid raid, Map<String, String> soloInstanceIds,
                                Map<String, String> duoInstanceIds, Map<String, String> trioInstanceIds, DateTimeFormatter formatter) {
        JSONObject values = activity.getJSONObject("values");
        double completedValue = values.getJSONObject("completed").getJSONObject("basic").getDouble("value");
        double playerCount = values.getJSONObject("playerCount").getJSONObject("basic").getDouble("value");
        long instanceId = activity.getJSONObject("activityDetails").getLong("instanceId");

        if (completedValue == 1.0 && playerCount == 3.0) {
            trioInstanceIds.put(raid.name(), String.valueOf(instanceId));
        }
        else if (completedValue == 1.0 && playerCount == 2.0) {
            duoInstanceIds.put(raid.name(), String.valueOf(instanceId));
        }
        else {
            soloInstanceIds.put(raid.name(), String.valueOf(instanceId));
        }
    }
    private void processRaids(Map<String, String> soloInstanceIds, Map<String, String> duoInstanceIds,
                              Map<String, String> trioInstanceIds) {

        StringBuilder resultBuilder = new StringBuilder();
        List<String> raidNames = Arrays.asList("LW", "SotP", "CoS", "GoS", "DSC", "VoG", "VotD", "KF", "RoN");
        Map<String, String> bestRaidResults = new HashMap<>();

        for (String raidName : raidNames) {
            String soloInstanceId = soloInstanceIds.get(raidName);
            String duoInstanceId = duoInstanceIds.get(raidName);
            String trioInstanceId = trioInstanceIds.get(raidName);

            Mono<Boolean> isFreshSolo = isFreshRaid(soloInstanceId);
            Mono<Boolean> isFlawlessSolo = isFlawlessRaid(soloInstanceId);
            Mono<Boolean> isFreshDuo = isFreshRaid(duoInstanceId);
            Mono<Boolean> isFlawlessDuo = isFlawlessRaid(duoInstanceId);
            Mono<Boolean> isFreshTrio = isFreshRaid(trioInstanceId);
            Mono<Boolean> isFlawlessTrio = isFlawlessRaid(trioInstanceId);

            Mono.zip(isFreshSolo, isFlawlessSolo, isFreshDuo, isFlawlessDuo, isFreshTrio, isFlawlessTrio)
                    .doOnNext(tuple -> {
                        boolean freshSolo = tuple.getT1();
                        boolean flawlessSolo = tuple.getT2();
                        boolean freshDuo = tuple.getT3();
                        boolean flawlessDuo = tuple.getT4();
                        boolean freshTrio = tuple.getT5();
                        boolean flawlessTrio = tuple.getT6();

                        String bestRaidResult = "";

                        if (flawlessSolo) {
                            bestRaidResult = "Fresh and Flawless Solo raid (instanceId: " + soloInstanceId + ")";
                        } else if (freshSolo) {
                            bestRaidResult = "Fresh Solo raid (instanceId: " + soloInstanceId + ")";
                        } else {
                            bestRaidResult = "Solo raid (instanceId: " + soloInstanceId + ")";
                        }

                        if (flawlessDuo) {
                            bestRaidResult = "Fresh and Flawless Duo raid (instanceId: " + duoInstanceId + ")";
                        } else if (freshDuo) {
                            bestRaidResult = "Fresh Duo raid (instanceId: " + duoInstanceId + ")";
                        } else {
                            bestRaidResult = "Duo raid (instanceId: " + duoInstanceId + ")";
                        }

                        if (flawlessTrio) {
                            bestRaidResult = "Fresh and Flawless Trio raid (instanceId: " + trioInstanceId + ")";
                        } else if (freshTrio) {
                            bestRaidResult = "Fresh Trio raid (instanceId: " + trioInstanceId + ")";
                        } else {
                            bestRaidResult = "Trio raid (instanceId: " + trioInstanceId + ")";
                        }

                        bestRaidResults.put(raidName, bestRaidResult);
                    })
                    .subscribe();
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Print the best raid results
        for (String raidName : raidNames) {
            resultBuilder.append(raidName).append("\n");
            resultBuilder.append(bestRaidResults.get(raidName)).append("\n\n");
        }

        System.out.println(resultBuilder);

        // You can use resultBuilder.toString() to get the final result as a String.
    }
    private Mono<Boolean> isFlawlessRaid(String instanceId) {
        String pgcrEndpoint = "/Destiny2/Stats/PostGameCarnageReport/{instanceId}/";

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path(pgcrEndpoint).build(instanceId))
                .retrieve()
                .bodyToMono(String.class)
                .map(responseBody -> {
                    JSONObject jsonObject = new JSONObject(responseBody);
                    JSONArray entries = jsonObject.getJSONObject("Response").getJSONArray("entries");

                    boolean isFlawless = true;

                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject entry = entries.getJSONObject(i);
                        JSONObject values = entry.getJSONObject("values");
                        int deaths = values.getJSONObject("deaths").getJSONObject("basic").getInt("value");

                        if (deaths > 0) {
                            isFlawless = false;
                            break;
                        }
                    }

                    return isFlawless;
                })
                .onErrorReturn(false);
    }
    private Mono<Boolean> isFreshRaid(String instanceId) {
        String pgcrEndpoint = "/Destiny2/Stats/PostGameCarnageReport/{instanceId}/";

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path(pgcrEndpoint).build(instanceId))
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(responseBody -> {
                    // Check if the raid is fresh
                    JSONObject jsonObject = new JSONObject(responseBody);
                    boolean activityStartedFromBeginning = jsonObject.getJSONObject("Response").getBoolean("activityWasStartedFromBeginning");

                    if (activityStartedFromBeginning) {
                        boolean isFreshRaid = true;
                    }

                    return Mono.just(activityStartedFromBeginning);
                })
                .onErrorReturn(false);
    }
}