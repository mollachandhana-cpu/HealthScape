package com.healthscapeai;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/environment")
public class EnvironmentalApiController {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentalApiController.class);
    private final EnvironmentalIntelligenceService service;
    private final EnvironmentalSnapshotRepository snapshots;
    private final AiBriefService aiBriefService;
    public EnvironmentalApiController(EnvironmentalIntelligenceService service, EnvironmentalSnapshotRepository snapshots, AiBriefService aiBriefService) { this.service = service; this.snapshots = snapshots; this.aiBriefService = aiBriefService; }

    @GetMapping("/search")
    public Map<String,Object> search(@RequestParam String q) {
        return Map.of("results", service.searchLocations(q));
    }

    @GetMapping
    public ResponseEntity<?> current(@RequestParam(defaultValue="17.3850") double lat,
                                     @RequestParam(defaultValue="78.4867") double lon,
                                     @RequestParam(defaultValue="Hyderabad, India") String place, @RequestParam(defaultValue="true") boolean persist) {
        try {
            Map<String,Object> result = service.getIntelligence(lat, lon, place);
            @SuppressWarnings("unchecked") Map<String,Object> current = (Map<String,Object>) result.get("current");
            // Saving history must never block live data. Do not persist exact browser geolocation.
            if (persist && (place == null || !place.equalsIgnoreCase("Your current location"))) {
                try {
                    snapshots.save(new EnvironmentalSnapshot(lat, lon, place, value(current,"usAqi"), value(current,"pm25"), value(current,"pm10"), value(current,"temperature"), value(current,"humidity"), value(current,"uvIndex")));
                } catch (Exception dbError) {
                    log.error("Could not save environmental snapshot (database problem, live data still returned)", dbError);
                }
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.warn("Open-Meteo rate limit reached: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("error", "The free weather data provider has reached its daily request limit. It resets daily; please try again later."));
        }
        catch (Exception e) {
            log.error("Open-Meteo request failed for lat={} lon={}", lat, lon, e);
            return ResponseEntity.status(502).body(Map.of("error", "Live environmental data is temporarily unavailable. Please retry shortly."));
        }
    }

    @PostMapping("/ai-brief")
    public ResponseEntity<?> aiBrief(@RequestBody Map<String,Object> evidence) {
        try { return ResponseEntity.ok(aiBriefService.explain(evidence)); }
        catch (Exception e) { return ResponseEntity.status(502).body(Map.of("error", "AI briefing is temporarily unavailable. The standard environmental insights are still available.")); }
    }

    @GetMapping("/history")
    public Map<String,Object> history(@RequestParam(defaultValue="17.3850") double lat, @RequestParam(defaultValue="78.4867") double lon) {
        var items = snapshots.findTop24ByLatitudeBetweenAndLongitudeBetweenOrderByCapturedAtDesc(lat-0.025,lat+0.025,lon-0.025,lon+0.025);
        return Map.of("count",items.size(),"items",items.stream().map(s -> Map.of("capturedAt",s.capturedAt.toString(),"place",s.place==null?"Selected location":s.place,"usAqi",s.usAqi,"pm25",s.pm25,"pm10",s.pm10,"temperature",s.temperature,"humidity",s.humidity,"uvIndex",s.uvIndex)).toList());
    }
    private double value(Map<String,Object> m,String key) { Object v=m.get(key); return v instanceof Number n?n.doubleValue():-1; }
}
