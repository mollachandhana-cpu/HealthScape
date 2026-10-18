
package com.healthscapeai;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/environment")
public class EnvironmentalApiController {

    private static final Logger log =
            LoggerFactory.getLogger(EnvironmentalApiController.class);

    private final EnvironmentalIntelligenceService service;
    private final EnvironmentalSnapshotRepository snapshots;
    private final AiBriefService aiBriefService;

    public EnvironmentalApiController(
            EnvironmentalIntelligenceService service,
            EnvironmentalSnapshotRepository snapshots,
            AiBriefService aiBriefService) {
        this.service = service;
        this.snapshots = snapshots;
        this.aiBriefService = aiBriefService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q) {
        try {
            return ResponseEntity.ok(
                    Map.of("results", service.searchLocations(q))
            );
        } catch (Exception e) {
            log.error("Environmental location search failed", e);

            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Location search is temporarily unavailable.")
            );
        }
    }

    @GetMapping
    public ResponseEntity<?> current(
            @RequestParam(defaultValue = "17.3850") double lat,
            @RequestParam(defaultValue = "78.4867") double lon,
            @RequestParam(defaultValue = "Hyderabad, India") String place,
            @RequestParam(defaultValue = "true") boolean persist) {

        try {
            // Validate coordinates before calling external services.
            if (!Double.isFinite(lat) || !Double.isFinite(lon)
                    || lat < -90 || lat > 90
                    || lon < -180 || lon > 180) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Invalid latitude or longitude.")
                );
            }

            Map<String, Object> result =
                    service.getIntelligence(lat, lon, place);

            Object currentObject = result.get("current");

            if (!(currentObject instanceof Map<?, ?> currentData)) {
                log.error("Environmental service returned no valid current data");

                return ResponseEntity.status(502).body(
                        Map.of("error",
                                "Live environmental data is temporarily unavailable. Please retry shortly.")
                );
            }

            // Persist a snapshot only when requested.
            // Do not persist the user's exact browser location.
            if (persist
                    && (place == null
                    || !place.equalsIgnoreCase("Your current location"))) {

                Map<String, Object> current = new java.util.HashMap<>();

                currentData.forEach((key, value) -> {
                    if (key instanceof String stringKey) {
                        current.put(stringKey, value);
                    }
                });

                EnvironmentalSnapshot snapshot =
                        new EnvironmentalSnapshot(
                                lat,
                                lon,
                                place,
                                value(current, "usAqi"),
                                value(current, "pm25"),
                                value(current, "pm10"),
                                value(current, "temperature"),
                                value(current, "humidity"),
                                value(current, "uvIndex")
                        );

                snapshots.save(snapshot);
            }

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid environmental request: {}", e.getMessage());

            return ResponseEntity.badRequest().body(
                    Map.of("error",
                            e.getMessage() == null
                                    ? "Invalid environmental request."
                                    : e.getMessage())
            );

        } catch (Exception e) {
            // The complete exception and stack trace will appear in Render logs.
            log.error(
                    "Environmental API failed for place '{}' (lat={}, lon={})",
                    place, lat, lon, e
            );

            return ResponseEntity.status(502).body(
                    Map.of("error",
                            "Live environmental data is temporarily unavailable. Please retry shortly.")
            );
        }
    }

    @PostMapping("/ai-brief")
    public ResponseEntity<?> aiBrief(
            @RequestBody Map<String, Object> evidence) {

        try {
            return ResponseEntity.ok(aiBriefService.explain(evidence));

        } catch (Exception e) {
            log.error("Environmental AI briefing failed", e);

            return ResponseEntity.status(502).body(
                    Map.of("error",
                            "AI briefing is temporarily unavailable. The standard environmental insights are still available.")
            );
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(
            @RequestParam(defaultValue = "17.3850") double lat,
            @RequestParam(defaultValue = "78.4867") double lon) {

        try {
            if (!Double.isFinite(lat) || !Double.isFinite(lon)
                    || lat < -90 || lat > 90
                    || lon < -180 || lon > 180) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Invalid latitude or longitude.")
                );
            }

            List<EnvironmentalSnapshot> items =
                    snapshots.findTop24ByLatitudeBetweenAndLongitudeBetweenOrderByCapturedAtDesc(
                            lat - 0.025,
                            lat + 0.025,
                            lon - 0.025,
                            lon + 0.025
                    );

            List<Map<String, Object>> historyItems = items.stream()
                    .map(s -> Map.<String, Object>of(
                            "capturedAt", s.capturedAt.toString(),
                            "place", s.place == null
                                    ? "Selected location" : s.place,
                            "usAqi", s.usAqi,
                            "pm25", s.pm25,
                            "pm10", s.pm10,
                            "temperature", s.temperature,
                            "humidity", s.humidity,
                            "uvIndex", s.uvIndex
                    ))
                    .toList();

            return ResponseEntity.ok(
                    Map.of(
                            "count", historyItems.size(),
                            "items", historyItems
                    )
            );

        } catch (Exception e) {
            log.error("Environmental history lookup failed", e);

            return ResponseEntity.internalServerError().body(
                    Map.of("error",
                            "Environmental history is temporarily unavailable.")
            );
        }
    }

    private double value(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number
                ? number.doubleValue()
                : -1;
    }
}
