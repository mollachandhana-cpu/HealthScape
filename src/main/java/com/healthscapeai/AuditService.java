package com.healthscapeai;

import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repo;
    public AuditService(AuditLogRepository repo) { this.repo = repo; }
    public void log(String actor, String action, String detail) { repo.save(new AuditLog(actor, action, detail)); }
}
