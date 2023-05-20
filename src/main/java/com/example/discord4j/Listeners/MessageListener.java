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
    public BungieApiController bungieApiController;
    public JokeApiController jokeApiController;
    public final QuoteController quoteController;

    @Autowired
    protected MessageListener() {
        quoteController = new QuoteController();
        jokeApiController = new JokeApiController();
    }

    private String extractUsername(String message) {
        String[] messageParts = message.split(" ", 2);
        return messageParts.length == 2 ? messageParts[1] : "";
    }

    protected Mono<Void> processCommand(Message eventMessage) {
        return Mono.just(eventMessage)
                .filter(message -> message.getAuthor()
                        .map(user -> !user.isBot()).orElse(false))
                .flatMap(message -> {
                    String content = message.getContent();
                    String command;
                    String bungieId;

                    if (content.startsWith("!day1")) {
                        command = "day1";
                        bungieId = extractUsername(content);
                        return processBungieCommand(bungieId, command, message);
                    } else if (content.startsWith("!lowman")) {
                        command = "lowman";
                        bungieId = extractUsername(content);
                        return processBungieCommand(bungieId, command, message);
                    } else if (content.equalsIgnoreCase("!help")) {
                        return helpCommand(message);
                    } else if (content.equalsIgnoreCase("!quote")) {
                        return quoteCommand(message);
                    } else if (content.equalsIgnoreCase("!was kap blackballed")) {
                        return miscCommand(message);
                    } else if (content.equalsIgnoreCase("!joke")) {
                        return jokeCommand(message);
                    }
                    return Mono.empty();
                }).then();
    }

    private Mono<Void> processBungieCommand(String bungieId, String command, Message message) {
        if (bungieId.isEmpty()) {
            return message.getChannel()
                    .flatMap(channel -> channel.createMessage(
                            "Please add your 'bungie_id' following the " +
                                    "command: !" + command +
                                    " (ex. '!" + command + " Aegis#8706')")).then();
        }
        bungieApiController.getMembershipInfo(bungieId, command, message);
        return Mono.empty();
    }
    private Mono<Void> helpCommand(Message message) {
        // handle the !help command
        return message.getChannel()
                .flatMap(channel -> channel.createMessage("""
                        TheQuickster handles the following commands:
                        \s
                        !day1 bungie_id
                        !lowman bungie_id (Please don't spam it, once every 10s is fine.)
                        !quote
                        !joke
                        !was kap blackballed
                        
                        __Note:__
                        ```!lowman iterates through up to 150 pages of an accounts raid activity and
                        checks every single clear for lowmans. Please respect Bungie API rates and 
                        limits by not abusing the command. The !day1 command only iterates through
                        raids attempted within the respective 24 hour period. So that command is fine.```
                        """))
                .then();
    }
    public Mono<Void> day1Command(String results, String raidName, Message message) {
        if (results == null || results.trim().isEmpty()) {
            return Mono.empty();
        }

        return message.getChannel()
                .flatMap(channel -> channel.createEmbed(spec -> buildDay1Embed(spec, results, raidName)))
                .then();
    }
    public Mono<Void> lowmanCommand(String results, Message message) {
        // handle the !lowman command
        if (results == null || results.trim().isEmpty()) {
            return Mono.empty();
        }

        return message.getChannel()
                .flatMap(channel -> channel.createEmbed(spec -> buildLowManEmbed(spec, results)))
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
    private void buildDay1Embed(LegacyEmbedCreateSpec spec, String results, String raidName) {
        spec.setColor(Color.of(255, 100, 100)); // Set the embed color
        spec.addField(raidName, results, false);

    }
    private void buildLowManEmbed(LegacyEmbedCreateSpec spec, String results) {
        spec.setColor(Color.of(255, 100, 100)); // Set the embed color
        spec.addField("", results, false);
    }
}