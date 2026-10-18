package com.healthscapeai;

import java.net.URI;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class WeatherService {

    private static final Logger log =
            LoggerFactory.getLogger(WeatherService.class);

    private final RestClient http = RestClient.create();

    private final VillageRepository repo;

    public WeatherService(VillageRepository repo) {
        this.repo = repo;
    }

    // Initial refresh after 5 minutes, then every 6 hours.
    @Scheduled(initialDelay = 300000, fixedRate = 21600000)
    public void refresh() {

        var list = repo.findAll();

        if (list.isEmpty()) {
            log.info("No localities found. Skipping weather refresh.");
            return;
        }

        log.info("Starting weather refresh for {} localities", list.size());

        for (var v : list) {

            try {
                String latitude = String.valueOf(v.latitude);
                String longitude = String.valueOf(v.longitude);

                // Fetch current weather for this locality.
                String weatherUrl =
                        "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=" + latitude
                        + "&longitude=" + longitude
                        + "&current=temperature_2m,relative_humidity_2m,precipitation"
                        + "&timezone=auto";

                JsonNode weather = http.get()
                        .uri(URI.create(weatherUrl))
                        .retrieve()
                        .body(JsonNode.class);

                JsonNode current = weather == null
                        ? null
                        : weather.path("current");

                if (current == null
                        || current.isMissingNode()
                        || current.isNull()) {

                    log.warn(
                            "Weather data unavailable for locality {}",
                            v.id
                    );
                    continue;
                }

                // Update only values actually returned by the API.
                if (current.hasNonNull("temperature_2m")) {
                    v.temperature =
                            current.get("temperature_2m").asDouble();
                }

                if (current.hasNonNull("relative_humidity_2m")) {
                    v.humidity =
                            current.get("relative_humidity_2m").asDouble();
                }

                if (current.hasNonNull("precipitation")) {
                    v.rainfall =
                            current.get("precipitation").asDouble();
                }

                // Fetch air quality independently.
                try {
                    String airUrl =
                            "https://air-quality-api.open-meteo.com/v1/air-quality"
                            + "?latitude=" + latitude
                            + "&longitude=" + longitude
                            + "&current=us_aqi";

                    JsonNode air = http.get()
                            .uri(URI.create(airUrl))
                            .retrieve()
                            .body(JsonNode.class);

                    JsonNode airCurrent = air == null
                            ? null
                            : air.path("current");

                    if (airCurrent != null
                            && airCurrent.hasNonNull("us_aqi")) {

                        v.aqi = airCurrent
                                .get("us_aqi")
                                .asDouble();
                    }

                } catch (Exception e) {
                    log.warn(
                            "Air quality refresh failed for locality {}: {}",
                            v.id,
                            e.getMessage()
                    );
                }

                v.source = "LIVE (Open-Meteo)";
                v.updated = Instant.now();

                repo.save(v);

                log.info(
                        "Weather refresh completed for locality {}",
                        v.id
                );

            } catch (Exception e) {
                log.warn(
                        "Weather refresh failed for locality {}: {}",
                        v.id,
                        e.getMessage()
                );
            }
        }

        log.info("Weather refresh process finished.");
    }
}
