package com.healthscapeai;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/** Pulls current weather and air quality for every locality from Open-Meteo (free, no key). */
@Service
public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private final RestClient http = RestClient.create();
    private final VillageRepository repo;
    public WeatherService(VillageRepository repo) { this.repo = repo; }

    @Scheduled(initialDelay = 5000, fixedRate = 1800000)
    public void refresh() {
        var list = repo.findAll();
        if (list.isEmpty()) return;
        String q = "latitude=" + list.stream().map(v -> String.valueOf(v.latitude)).collect(Collectors.joining(","))
                 + "&longitude=" + list.stream().map(v -> String.valueOf(v.longitude)).collect(Collectors.joining(","));
        try {
            JsonNode w = http.get().uri(URI.create("https://api.open-meteo.com/v1/forecast?" + q
                    + "&current=temperature_2m,relative_humidity_2m,precipitation&timezone=auto")).retrieve().body(JsonNode.class);
            JsonNode a = http.get().uri(URI.create("https://air-quality-api.open-meteo.com/v1/air-quality?" + q
                    + "&current=us_aqi")).retrieve().body(JsonNode.class);
            for (int i = 0; i < list.size(); i++) {
                var v = list.get(i); JsonNode c = w.get(i).get("current"), ac = a.get(i).get("current");
                v.temperature = c.get("temperature_2m").asDouble(v.temperature);
                v.humidity = c.get("relative_humidity_2m").asDouble(v.humidity);
                v.rainfall = c.get("precipitation").asDouble(v.rainfall);
                if (ac != null && ac.hasNonNull("us_aqi")) v.aqi = ac.get("us_aqi").asDouble();
                v.source = "LIVE (Open-Meteo)"; v.updated = Instant.now();
            }
            repo.saveAll(list);
            log.info("Refreshed live conditions for {} localities", list.size());
        } catch (Exception e) { log.warn("Live data refresh failed; keeping previous values: {}", e.getMessage()); }
    }
}
