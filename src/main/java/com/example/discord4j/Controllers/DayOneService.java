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
    private final ConcreteMessageListener listener;
    private final Map<String, Raid> raids = Raid.getRaids(); // Call HashMap in Models.Raid

    @Autowired
    protected DayOneService(WebClient webClient) {
        this.webClient = webClient;
        this.listener = new ConcreteMessageListener();
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

        for (int i = 0; i < activities.length(); i++) {
            JSONObject activity = activities.getJSONObject(i);
            long activityHash = activity.getJSONObject("activityDetails").getLong("referenceId");

            raidList.stream()
                    .filter(raid -> raid != null && Long.toString(activityHash).equals(raid.hash()))
                    .findFirst()
                    .ifPresent(raid -> processRaid(activity, raid, message));
        }
    }

    private void processRaid(JSONObject activity, Raid raid, Message message) {
        JSONObject values = activity.getJSONObject("values");
        double completedValue = values.getJSONObject("completed").getJSONObject("basic").getDouble("value");

        if (completedValue == 1.0) {
            Instant startTime = Instant.parse(activity.getString("period"));
            long activityDurationSeconds = values.getJSONObject("activityDurationSeconds").getJSONObject("basic").getLong("value");
            Instant completionTime = startTime.plusSeconds(activityDurationSeconds);
            Instant raidLaunchTime = Instant.parse(raid.releaseTime());
            Duration timeDifference = Duration.between(raidLaunchTime, completionTime);

            if (timeDifference.toHours() <= raid.hours()) {
                long hoursAfterLaunch = timeDifference.toHours();
                long minutesAfterLaunch = timeDifference.toMinutes() % 60;

                long instanceId = activity.getJSONObject("activityDetails").getLong("instanceId");
                String duration = values.getJSONObject("activityDurationSeconds").getJSONObject("basic").getString("displayValue");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

                String time = completionTime.atZone(ZoneId.systemDefault()).format(formatter);
                String result = String.format("""
                                Cleared %dh %dm post launch @ %s UTC
                                Instance Duration: %s -> <https://raid.report/pgcr/%d>
                                """, hoursAfterLaunch,
                        minutesAfterLaunch, time, duration, instanceId);

                System.out.println(result);
                listener.day1Command(result, raid.name(), message).subscribe();
            }
        }
    }
}