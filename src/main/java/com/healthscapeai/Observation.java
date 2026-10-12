package com.healthscapeai;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Observation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long villageId; public String villageName, type, severity, author, source = "FIELD OBSERVATION";
    @Column(length = 500) public String note;
    public Instant createdAt = Instant.now();
}
