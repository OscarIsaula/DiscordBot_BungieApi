package com.example.discord4j.Models;

import java.util.HashMap;
import java.util.Map;

public record Raid(String name, String hash, String releaseTime, int hours) {
    public static Map<String, Raid> getRaids() {
        Map<String, Raid> raids = new HashMap<>();
        raids.put("LW", new Raid("Last Wish", "2122313384",
                "2018-09-14T17:00:00Z", 24));
        raids.put("SotP", new Raid("Scourge of the Past", "548750096",
                "2018-12-07T17:00:00Z", 24));
        raids.put("CoS", new Raid("Crown of Sorrow", "3333172150",
                "2019-06-04T17:00:00Z", 24));
        raids.put("GoS", new Raid("Garden of Salvation", "3458480158",
                "2019-10-05T17:00:00Z", 24));
        raids.put("DSC", new Raid("Deep Stone Crypt", "910380154",
                "2020-11-21T17:00:00Z", 24));
        raids.put("VoG", new Raid("Vault of Glass", "3881495763",
                "2021-05-22T17:00:00Z", 24));
        raids.put("VoG2", new Raid("Vault of Glass Challenge Mode",
                "1485585878", "2021-05-22T17:00:00Z", 24));
        raids.put("VotD", new Raid("Vow of the Disciple", "1441982566",
                "2022-03-05T17:00:00Z", 48));
        raids.put("KF", new Raid("Kings Fall", "1374392663",
                "2022-08-26T17:00:00Z", 24));
        raids.put("KF2", new Raid("Kings Fall Challenge Mode",
                "1063970578", "2022-08-26T17:00:00Z", 24));
        raids.put("RoN", new Raid("Root of Nightmares", "2381413764",
                "2023-03-10T17:00:00Z", 48));
        return raids;
    }
}
