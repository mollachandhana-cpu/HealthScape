package com.healthscapeai;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "audit_log")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String actor, action;
    @Column(length = 500) public String detail;
    public Instant created = Instant.now();
    protected AuditLog() {}
    AuditLog(String actor, String action, String detail) { this.actor = actor; this.action = action; this.detail = detail; }
}
