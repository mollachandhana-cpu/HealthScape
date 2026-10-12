package com.healthscapeai;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class AlertEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long alertId; public String status, actor;
    @Column(length = 400) public String reason;
    @Column(name = "event_time") public Instant at = Instant.now();
}
