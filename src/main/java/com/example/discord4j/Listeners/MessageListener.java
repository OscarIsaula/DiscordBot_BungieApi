package com.example.discord4j.Listeners;

import com.example.discord4j.Controllers.JokeApiController;
import com.example.discord4j.Controllers.QuoteController;
import com.example.discord4j.Controllers.BungieApiController;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.legacy.LegacyEmbedCreateSpec;
import discord4j.rest.util.Color;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Random;

@Component
public abstract class MessageListener {

    @Autowired
    public JokeApiController jokeApiController;
    @Autowired
    public BungieApiController bungieApiController;
    public final QuoteController quoteController;

    @Autowired
    protected MessageListener() {
        quoteController = new QuoteController();
    }

    private String extractUsername(String message) {
        String[] messageParts = message.split(" ", 2);
        return messageParts.length == 2 ? messageParts[1] : "";
    }

    public Mono<Void> processCommand(Message eventMessage) {
        return Mono.just(eventMessage)
                .filter(message -> message.getAuthor()
                        .map(user -> !user.isBot()).orElse(false))
                .flatMap(message -> {
                    String content = message.getContent();
                    if (content.startsWith("!day1")) {
                        String command = "day1";
                        String bungieId = extractUsername(content);
                        if (bungieId.isEmpty()) {
                            return message.getChannel()
                                    .flatMap(channel -> channel.createMessage(
                                    "Please add your 'bungie_id' following the " +
                                            "command: !day1 (ex. '!day1 Aegis#8706')"));
                        }
                        bungieApiController.getMembershipInfo
                                (bungieId, command, message);
                    } else if (content.startsWith("!lowman")) {
                        String command = "lowman";
                        String bungieId = extractUsername(content);
                        if (bungieId.isEmpty()) {
                            return message.getChannel()
                                    .flatMap(channel -> channel.createMessage(
                                    "Please add your 'bungie_id' following the " +
                                            "command: !lowman (ex. '!lowman Aegis#8706')"));
                        }
                        bungieApiController.getMembershipInfo
                                (bungieId, command, message);
                    } else if (content.equalsIgnoreCase("!help")) {
                        return helpCommand(message);
                    } else if (content.equalsIgnoreCase("!quote")) {
                        return quoteCommand(message);
                    } else if (content.equalsIgnoreCase
                            ("!was kap blackballed")) {
                        return miscCommand(message);
                    } else if (content.equalsIgnoreCase("!joke")) {
                        return jokeCommand(message);
                    }
                    return Mono.empty();
                    }).then();
    }
    private Mono<Void> helpCommand(Message message) {
        // handle the !help command
        return message.getChannel()
                .flatMap(channel -> channel.createMessage("""
                        The following commands are currently active:
                        \s
                        !day1 bungie_id
                        !lowman bungie_id (Under Construction)
                        !quote
                        !joke
                        !was kap blackballed

                        'If there's anything else you need...\040
                        please hesitate to ask' -Squidward\040
                        (Bubble Buddy, Nov 2000)
                        """))
                .then();
    }
    public Mono<Void> day1Command(String results, Message message) {
        if (results == null || results.trim().isEmpty()) {
            return Mono.empty();
        }

        String[] resultLines = results.split("\n");

        return message.getChannel()
                .flatMap(channel -> channel.createEmbed(spec -> buildEmbed(spec, resultLines)))
                .then();
    }
    public Mono<Void> lowmanCommand(String results, Message message) {
        // handle the !lowman command
        if (results == null || results.trim().isEmpty()) {
            return Mono.empty();
        }

        return message.getChannel()
                .flatMap(channel -> channel.createMessage(results))
                .then();
    }
    private String getRandomQuote(List<String> quotes) {
        int randomIndex = new Random().nextInt(quotes.size());
        return quotes.get(randomIndex);
    }
    private Mono<Void> quoteCommand(Message message) {
        // handle the !quote command
        List<String> quotes = quoteController.readQuotesFromFile();
        String randomQuote = getRandomQuote(quotes);
        return message.getChannel()
                .flatMap(channel -> channel.createMessage(randomQuote))
                .then();
    }
    private Mono<Void> miscCommand(Message message) {
        // handle the random misc command
        return message.getChannel()
                .flatMap(channel -> channel.createMessage("no"))
                .then();
    }
    private Mono<Void> jokeCommand(Message message) {
        // handle the !joke command
        return message.getChannel()
                .flatMap(channel -> jokeApiController.getRandomJoke()
                        .flatMap(channel::createMessage)
                )
                .then();
    }

    private void buildEmbed(LegacyEmbedCreateSpec spec, String[] resultLines) {
        spec.setColor(Color.of(255, 100, 100)); // Set the embed color

        for (int i = 0; i < resultLines.length; i += 3) {
            String title = resultLines[i].replace("Raid Report: ", "").trim();
            String url = resultLines[i + 1].trim();
            String description = resultLines[i + 2].trim();

            spec.addField("", "" + title + "\n" + url + "\n" + description, false);
        }
    }



}