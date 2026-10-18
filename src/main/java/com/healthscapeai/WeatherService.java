
package com.healthscapeai;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class WeatherService {

    private static final Logger log =
            LoggerFactory.getLogger(WeatherService.class);

    private static final int BATCH_SIZE = 10;

    private final RestClient http = RestClient.create();
    private final VillageRepository repo;

    public WeatherService(VillageRepository repo) {
        this.repo = repo;
    }

    // Initial refresh after 5 minutes, then every 6 hours.
    @Scheduled(initialDelay = 300000, fixedRate = 21600000)
    public void refresh() {

        List<?> villages = repo.findAll();

        if (villages.isEmpty()) {
            log.info("No localities found. Skipping refresh.");
            return;
        }

        log.info("Refreshing data for {} localities", villages.size());

        // Process small batches to reduce API requests.
        for (int start = 0; start < villages.size();
             start += BATCH_SIZE) {

            int end = Math.min(start + BATCH_SIZE, villages.size());

            // Keep a typed list of the entities returned by the repository.
            refreshBatch(villages.subList(start, end));
        }

        log.info("Weather refresh process finished.");
    }

    @SuppressWarnings("unchecked")
    private void refreshBatch(List<?> batch) {

        // This method delegates to the typed implementation below.
        refreshTypedBatch((List<com.healthscapeai.Village>)
                (List<?>) batch);
    }

    private void refreshTypedBatch(List<Village> batch) {

        StringBuilder latitudes = new StringBuilder();
        StringBuilder longitudes = new StringBuilder();

        for (Village v : batch) {
            if (latitudes.length() > 0) {
                latitudes.append(",");
                longitudes.append(",");
            }

            latitudes.append(v.latitude);
            longitudes.append(v.longitude);
        }

        String lat = latitudes.toString();
        String lon = longitudes.toString();

        JsonNode weatherResponse = null;
        JsonNode airResponse = null;

        try {
            String url =
                    "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + lat
                    + "&longitude=" + lon
                    + "&current=temperature_2m,"
                    + "relative_humidity_2m,precipitation"
                    + "&timezone=auto";

            weatherResponse = http.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(JsonNode.class);

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Weather API rate limit reached. "
                    + "Skipping this batch; no retry will be attempted.");
            return;

        } catch (Exception e) {
            log.warn("Weather batch request failed: {}",
                    e.getMessage());
        }

        try {
            String url =
                    "https://air-quality-api.open-meteo.com/v1/air-quality"
                    + "?latitude=" + lat
                    + "&longitude=" + lon
                    + "&current=us_aqi";

            airResponse = http.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(JsonNode.class);

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Air-quality API rate limit reached. "
                    + "Keeping existing AQI values.");

        } catch (Exception e) {
            log.warn("Air-quality batch request failed: {}",
                    e.getMessage());
        }

        for (int i = 0; i < batch.size(); i++) {
            Village v = batch.get(i);
            boolean changed = false;

            JsonNode weather = locationNode(
                    weatherResponse, i, batch.size());

            JsonNode current = weather == null
                    ? null : weather.path("current");

            if (current != null && !current.isMissingNode()
                    && !current.isNull()) {

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

                v.source = "LIVE (Open-Meteo)";
                v.updated = Instant.now();
                changed = true;
            }

            JsonNode air = locationNode(
                    airResponse, i, batch.size());

            JsonNode airCurrent = air == null
                    ? null : air.path("current");

            if (airCurrent != null
                    && airCurrent.hasNonNull("us_aqi")) {

                v.aqi = airCurrent.get("us_aqi").asDouble();
                changed = true;
            }

            if (changed) {
                repo.save(v);
                log.info("Updated locality {}", v.id);
            }
        }
    }

    private JsonNode locationNode(
            JsonNode response, int index, int batchSize) {

        if (response == null || response.isNull()) {
            return null;
        }

        if (response.isArray()) {
            return index < response.size()
                    ? response.get(index) : null;
        }

        // A single coordinate normally returns a JSON object.
        return batchSize == 1 ? response : null;
    }
}
