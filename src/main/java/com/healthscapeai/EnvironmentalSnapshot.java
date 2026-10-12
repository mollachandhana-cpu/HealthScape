package com.healthscapeai;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="environmental_snapshots", indexes={@Index(name="idx_env_snapshot_location_time", columnList="latitude,longitude,captured_at")})
public class EnvironmentalSnapshot {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    double latitude, longitude;
    String place;
    double usAqi, pm25, pm10, temperature, humidity, uvIndex;
    @Column(name="captured_at", nullable=false) Instant capturedAt = Instant.now();
    protected EnvironmentalSnapshot() {}
    EnvironmentalSnapshot(double lat,double lon,String place,double aqi,double pm25,double pm10,double temperature,double humidity,double uv) {
        this.latitude=lat;this.longitude=lon;this.place=place;this.usAqi=aqi;this.pm25=pm25;this.pm10=pm10;this.temperature=temperature;this.humidity=humidity;this.uvIndex=uv;
    }
}
