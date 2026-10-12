package com.healthscapeai;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Village {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    String name, zone, source = "SAMPLE DEFAULTS";
    double latitude, longitude;
    double temperature = 30, humidity = 60, rainfall = 0, aqi = 60, waterQuality = 75, sanitation = 75,
           population = 40000, populationDensity = 6000, caseCount = 0;
    Instant updated;

    protected Village() {}
    Village(String name, String zone, double lat, double lon) { this.name = name; this.zone = zone; latitude = lat; longitude = lon; }
    boolean fresh() { return updated != null && updated.isAfter(Instant.now().minus(3, java.time.temporal.ChronoUnit.HOURS)); }
    RiskInput toInput() {
        return new RiskInput(caseCount, rainfall, temperature, humidity, aqi, waterQuality, sanitation, population, populationDensity);
    }
}
