package com.example.discord4j.Controllers;

import com.example.discord4j.Listeners.ConcreteMessageListener;
import com.example.discord4j.Models.Raid;
import discord4j.core.object.entity.Message;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DayOneService {
    private final WebClient webClient;
    private final Map<String, Raid> raids = Raid.getRaids(); // Call HashMap in Models.Raid

    @Autowired
    public DayOneService(WebClient webClient) {
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
    private void parseActivityHistory(String responseBody, Message message) {
        StringBuilder result = new StringBuilder();
        JSONObject jsonObject = new JSONObject(responseBody);
        JSONObject responseData = jsonObject.getJSONObject("Response");
        if (!responseData.has("activities")) {
            return;
        }
        JSONArray activities = responseData.getJSONArray("activities");

        List<Raid> raidList = Arrays.asList(raids.get("LW"), raids.get("SotP"),
                raids.get("CoS"), raids.get("GoS"), raids.get("DSC"), raids.get("VoG"),
                raids.get("VoG2"), raids.get("VotD"), raids.get("KF"), raids.get("KF2"),
                raids.get("RoN"));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (int i = 0; i < activities.length(); i++) {
            JSONObject activity = activities.getJSONObject(i);
            long activityHash = activity.getJSONObject("activityDetails").getLong("referenceId");

            raidList.stream()
                    .filter(raid -> raid != null && Long.toString(activityHash).equals(raid.getHash()))
                    .findFirst()
                    .ifPresent(raid -> processRaid(activity, raid, result, formatter));
        }
        String trimmedResult = result.toString().trim();
        String cleanedResult = trimmedResult.replaceAll("\\s*\\n\\s*", "\n");
        System.out.println(cleanedResult);
        ConcreteMessageListener listener = new ConcreteMessageListener();
        listener.day1Command(cleanedResult, message).subscribe();
    }

    private void processRaid(JSONObject activity, Raid raid, StringBuilder result, DateTimeFormatter formatter) {
        JSONObject values = activity.getJSONObject("values");
        double completedValue = values.getJSONObject("completed").getJSONObject("basic").getDouble("value");

        if (completedValue == 1.0) {
            Instant startTime = Instant.parse(activity.getString("period"));
            long activityDurationSeconds = values.getJSONObject("activityDurationSeconds").getJSONObject("basic").getLong("value");
            Instant completionTime = startTime.plusSeconds(activityDurationSeconds);
            Instant raidLaunchTime = Instant.parse(raid.getReleaseTime());
            Duration timeDifference = Duration.between(raidLaunchTime, completionTime);

            if (timeDifference.toHours() <= raid.getHours()) {
                long hoursAfterLaunch = timeDifference.toHours();
                long minutesAfterLaunch = timeDifference.toMinutes() % 60;

                long hours = activityDurationSeconds / 3600;
                long minutes = (activityDurationSeconds % 3600) / 60;
                long seconds = activityDurationSeconds % 60;

                long instanceId = activity.getJSONObject("activityDetails").getLong("instanceId");

                String time = completionTime.atZone(ZoneId.systemDefault()).format(formatter);
                result.append(raid.getName()).append(": <https://raid.report/pgcr/").append(instanceId).append(">\n");
                result.append("Cleared ").append(hoursAfterLaunch).append("h ").append
                        (minutesAfterLaunch).append("m post launch @ ").append(time).append(" EST\n");
                result.append("Instance duration: ").append(hours).append("h ").append(minutes)
                        .append("m ").append(seconds).append("s\n");
            }
        }
    }
}