package com.healthscapeai;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
public class WebController {
    public record VillageView(Long id, String name, String zone, double lat, double lon, double temperature, double humidity,
            double rainfall, double aqi, double waterQuality, double sanitation, double cases, String level, double score,
            List<RiskService.Factor> factors, List<String> precautions, String source, String updated, String alert) {}
    private static final String NOTE = "Rule-based demonstration score. Not clinically validated and not a diagnosis. Follow official health advisories.";

    private final VillageRepository repo; private final RiskService risk;
    private final AuditService audit; private final AlertRepository alerts; private final AlertService alertService;
    public WebController(VillageRepository repo, RiskService risk, AuditService audit, AlertRepository alerts, AlertService alertService) {
        this.repo = repo; this.risk = risk; this.audit = audit; this.alerts = alerts; this.alertService = alertService;
    }

    private List<VillageView> views() {
        return repo.findAll().stream().map(v -> {
            var r = risk.assess(v.toInput()); var pre = risk.precautions(r.level(), r.factors());
            String alert = "HealthScape Alert: " + r.level() + " environmental health risk in " + v.name + ". " + pre.get(0);
            return new VillageView(v.id, v.name, v.zone, v.latitude, v.longitude, v.temperature, v.humidity, v.rainfall, v.aqi,
                    v.waterQuality, v.sanitation, v.caseCount, v.fresh() ? r.level() : "Unknown", r.score(), r.factors(), pre, v.source,
                    v.updated == null ? "not yet refreshed" : v.updated.toString(), alert);
        }).sorted(Comparator.comparing(VillageView::name)).toList();
    }
    private void counts(Model m, List<VillageView> l) {
        for (String s : List.of("Low", "Medium", "High", "Unknown")) m.addAttribute(s.toLowerCase(), l.stream().filter(v -> v.level().equals(s)).count());
        m.addAttribute("villages", l); m.addAttribute("note", NOTE);
    }

    @GetMapping("/") public String home(Model m) { counts(m, views());
        m.addAttribute("advisories", alerts.findAllByOrderByCreatedAtDesc().stream().filter(a -> !a.status.equals("NEW") && !a.status.equals("RESOLVED")).toList());
        return "index"; }
    @GetMapping("/login") public String login() { return "login"; }
    @GetMapping("/air-quality") public String airQuality() { return "air-quality"; }
    @GetMapping("/climate-risk") public String climateRisk() { return "climate-risk"; }
    @GetMapping("/data-methodology") public String methodology() { return "methodology"; }
    @GetMapping("/official") public String official(Model m) { counts(m, views()); return "official"; }

    @PostMapping("/official/update")
    public String update(@RequestParam Long id, @RequestParam double caseCount, @RequestParam double waterQuality,
                         @RequestParam double sanitation, @RequestParam double population, org.springframework.security.core.Authentication auth) {
        boolean ok = caseCount >= 0 && population > 0 && waterQuality >= 0 && waterQuality <= 100 && sanitation >= 0 && sanitation <= 100;
        if (ok) repo.findById(id).ifPresent(v -> { v.caseCount = caseCount; v.waterQuality = waterQuality;
            v.sanitation = sanitation; v.population = population; repo.save(v);
            audit.log(auth.getName(), "UPDATE_AREA", v.name + " cases=" + caseCount + " water=" + waterQuality + " sanitation=" + sanitation); });
        alertService.evaluate(); return "redirect:/official";
    }

    @GetMapping("/api/villages") @ResponseBody
    public Map<String, Object> villages() { var l = views(); return Map.of("count", l.size(), "villages", l, "note", NOTE); }

    @PostMapping("/api/predict") @ResponseBody
    public Map<String, Object> predict(@Valid @RequestBody RiskInput in) {
        var r = risk.assess(in);
        return Map.of("score", r.score(), "level", r.level(), "factors", r.factors(), "note", NOTE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class) @ResponseBody
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        var f = e.getBindingResult().getFieldError();
        return ResponseEntity.badRequest().body(Map.of("error", f == null ? "Invalid input" : f.getField() + " " + f.getDefaultMessage()));
    }
}
