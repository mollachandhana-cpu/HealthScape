package com.healthscapeai;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/** Public-facing area intelligence: evidence-led, and explicit when local evidence is stale or absent. */
@Controller
public class AreaIntelligenceController {
    public record AreaOption(Long id, String name, String zone, String level, boolean fresh) {}
    public record Evidence(String label, String value, String interpretation, boolean available) {}

    private final VillageRepository villages;
    private final RiskService risk;
    private final ObservationRepository observations;
    private final AlertRepository alerts;

    public AreaIntelligenceController(VillageRepository villages, RiskService risk,
                                      ObservationRepository observations, AlertRepository alerts) {
        this.villages = villages;
        this.risk = risk;
        this.observations = observations;
        this.alerts = alerts;
    }

    @GetMapping("/area-intelligence")
    public String page(@RequestParam(required = false) Long areaId, Model model) {
        var all = villages.findAll().stream().sorted(Comparator.comparing(v -> v.name)).toList();
        var selected = all.stream().filter(v -> areaId != null && areaId.equals(v.id)).findFirst()
                .orElseGet(() -> all.isEmpty() ? null : all.get(0));
        List<AreaOption> options = all.stream().map(v -> {
            var assessment = risk.assess(v.toInput());
            return new AreaOption(v.id, v.name, v.zone, v.fresh() ? assessment.level() : "Unknown", v.fresh());
        }).toList();
        model.addAttribute("areas", options);
        model.addAttribute("selected", selected);
        model.addAttribute("hasArea", selected != null);
        model.addAttribute("updatedLabel", selected == null || selected.updated == null ? "No verified update recorded"
                : DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'").withZone(ZoneId.of("Asia/Kolkata")).format(selected.updated));
        model.addAttribute("ageMinutes", selected == null || selected.updated == null ? null : Duration.between(selected.updated, Instant.now()).toMinutes());
        model.addAttribute("fresh", selected != null && selected.fresh());
        if (selected != null) {
            var assessment = risk.assess(selected.toInput());
            model.addAttribute("assessment", assessment);
            model.addAttribute("factors", selected.fresh() ? assessment.factors() : List.of());
            model.addAttribute("precautions", selected.fresh() ? risk.precautions(assessment.level(), assessment.factors()) : List.of());
            model.addAttribute("evidence", List.of(
                    new Evidence("Air quality index", selected.aqi > 0 ? String.format("%.0f AQI", selected.aqi) : "Unavailable", "Outdoor model/record value; not a local station reading", selected.fresh()),
                    new Evidence("Rainfall", String.format("%.1f mm", selected.rainfall), "Stored area value; interpretation depends on the reporting period", selected.fresh()),
                    new Evidence("Water quality score", String.format("%.0f / 100", selected.waterQuality), "Requires a documented measurement method", selected.fresh()),
                    new Evidence("Sanitation score", String.format("%.0f / 100", selected.sanitation), "Requires a verified field assessment", selected.fresh()),
                    new Evidence("Health reports", String.format("%.0f", selected.caseCount), "Aggregated count; not a confirmed outbreak signal", selected.fresh())
            ));
            model.addAttribute("recentObservations", observations.findTop30ByOrderByCreatedAtDesc().stream()
                    .filter(o -> selected.id.equals(o.villageId)).limit(5).toList());
            model.addAttribute("openAlertCount", alerts.findAllByOrderByCreatedAtDesc().stream()
                    .filter(a -> selected.id.equals(a.villageId) && !"RESOLVED".equals(a.status)).count());
            model.addAttribute("source", selected.source == null || selected.source.isBlank() ? "Not specified" : selected.source);
        } else {
            model.addAttribute("assessment", null);
            model.addAttribute("factors", List.of());
            model.addAttribute("precautions", List.of());
            model.addAttribute("evidence", List.of());
            model.addAttribute("recentObservations", List.of());
            model.addAttribute("openAlertCount", 0);
            model.addAttribute("source", "Not specified");
        }
        return "area-intelligence";
    }
}
