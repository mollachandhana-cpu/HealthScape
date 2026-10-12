package com.healthscapeai;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Alert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long villageId; public String villageName, severity, status = "NEW", assignee;
    @Column(length = 600) public String summary;
    public Instant createdAt = Instant.now(), updatedAt = Instant.now();
}
