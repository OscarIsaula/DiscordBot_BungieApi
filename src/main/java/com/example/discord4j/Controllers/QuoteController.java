package com.example.discord4j.Controllers;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class QuoteController {

    public List<String> readQuotesFromFile() {
        List<String> quotes = new ArrayList<>();
        try {
            Resource resource = new ClassPathResource("quotes.txt");
            InputStream inputStream = resource.getInputStream();
            BufferedReader reader = new BufferedReader
                    (new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                quotes.add(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return quotes;
    }
}