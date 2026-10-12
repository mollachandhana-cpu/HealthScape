package com.healthscapeai;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AlertService {
    static final List<String> FLOW = List.of("NEW", "ACKNOWLEDGED", "UNDER_INVESTIGATION", "ACTION_IN_PROGRESS", "RESOLVED");
    private final VillageRepository villages; private final RiskService risk;
    private final AlertRepository alerts; private final AlertEventRepository events;
    public AlertService(VillageRepository v, RiskService r, AlertRepository a, AlertEventRepository e) { villages = v; risk = r; alerts = a; events = e; }

    static String next(String s) { int i = FLOW.indexOf(s); return i >= 0 && i < FLOW.size() - 1 ? FLOW.get(i + 1) : null; }

    /** Rule: a High risk level opens one alert per area; no new alert while one is unresolved. */
    @Scheduled(initialDelay = 30000, fixedRate = 600000) @Transactional
    public void evaluate() {
        for (Village v : villages.findAll()) {
            var r = risk.assess(v.toInput());
            if (!r.level().equals("High") || alerts.existsByVillageIdAndStatusNot(v.id, "RESOLVED")) continue;
            Alert a = new Alert(); a.villageId = v.id; a.villageName = v.name; a.severity = "High";
            a.summary = "Potential environmental health concern: risk score " + r.score() + " (High). Main contributors: "
                    + r.factors().stream().limit(3).map(RiskService.Factor::feature).collect(Collectors.joining(", ")) + ".";
            alerts.save(a); log(a.id, "NEW", "system", "Rule: risk score reached High (" + r.score() + ") using " + v.source);
        }
    }

    @Transactional
    public void fromField(Village village, Observation observation) {
        if (village == null || observation == null) return;
        if (alerts.existsByVillageIdAndStatusNot(village.id, "RESOLVED")) return;
        Alert a = new Alert();
        a.villageId = village.id;
        a.villageName = village.name;
        a.severity = "High";
        a.summary = "High-severity field report: " + observation.type + ". " +
                (observation.note == null || observation.note.isBlank() ? "No additional notes." : observation.note);
        alerts.save(a);
        log(a.id, "NEW", observation.author == null ? "system" : observation.author,
                "Created from high-severity field observation");
    }

    @Transactional
    public String move(Long id, String to, String actor, String reason) {
        Alert a = alerts.findById(id).orElse(null);
        if (a == null) return "Alert not found.";
        if (!to.equals(next(a.status))) return "That status change is not allowed from " + a.status + ".";
        if (reason == null || reason.isBlank() || reason.length() > 400) return "A reason (max 400 characters) is required.";
        a.status = to; a.updatedAt = Instant.now(); alerts.save(a); log(id, to, actor, reason.trim()); return null;
    }

    @Transactional
    public String assign(Long id, String assignee, String actor) {
        Alert a = alerts.findById(id).orElse(null);
        if (a == null) return "Alert not found.";
        a.assignee = assignee; a.updatedAt = Instant.now(); alerts.save(a); log(id, a.status, actor, "Assigned to " + assignee); return null;
    }

    private void log(Long id, String status, String actor, String reason) {
        AlertEvent e = new AlertEvent(); e.alertId = id; e.status = status; e.actor = actor; e.reason = reason; events.save(e);
    }
}
