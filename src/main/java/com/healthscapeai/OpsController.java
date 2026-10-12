package com.healthscapeai;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
public class OpsController {
    public record AlertView(Alert a, String next, List<AlertEvent> events) {}
    public record Opt(Long id, String name) {}
    private static final List<String> TYPES = List.of("Unusual mosquito activity", "Suspected water contamination", "Heat-related concern", "Poor drainage or sanitation", "Other");
    private static final List<String> LEVELS = List.of("Low", "Moderate", "High");

    private final AlertRepository alerts; private final AlertEventRepository events; private final AlertService svc;
    private final ObservationRepository obs; private final VillageRepository villages;
    private final AppUserRepository users; private final PasswordEncoder enc; private final AuditLogRepository auditLogs; private final AuditService audit;
    public OpsController(AlertRepository a, AlertEventRepository e, AlertService s, ObservationRepository o, VillageRepository v,
                         AppUserRepository u, PasswordEncoder p, AuditLogRepository auditLogs, AuditService audit) {
        alerts = a; events = e; svc = s; obs = o; villages = v; users = u; enc = p; this.auditLogs = auditLogs; this.audit = audit;
    }

    @GetMapping("/official/alerts")
    public String page(Model m) {
        m.addAttribute("alerts", alerts.findAllByOrderByCreatedAtDesc().stream()
                .map(a -> new AlertView(a, AlertService.next(a.status), events.findByAlertIdOrderByAtAsc(a.id))).toList());
        m.addAttribute("opts", villages.findAll().stream().map(v -> new Opt(v.id, v.name)).sorted(java.util.Comparator.comparing(Opt::name)).toList());
        m.addAttribute("people", users.findAll().stream().map(u -> u.username).toList());
        m.addAttribute("observations", obs.findTop30ByOrderByCreatedAtDesc());
        m.addAttribute("types", TYPES);
        return "alerts";
    }

    @PostMapping("/official/alerts/{id}/status")
    public String status(@PathVariable Long id, @RequestParam String to, @RequestParam String reason, Authentication au, RedirectAttributes ra) {
        String err = svc.move(id, to, au.getName(), reason);
        ra.addFlashAttribute("msg", err == null ? "Alert updated." : err); return "redirect:/official/alerts";
    }

    @PostMapping("/official/alerts/{id}/assign")
    public String assign(@PathVariable Long id, @RequestParam String assignee, Authentication au, RedirectAttributes ra) {
        boolean known = users.findByUsername(assignee).isPresent();
        ra.addFlashAttribute("msg", !known ? "Unknown user." : svc.assign(id, assignee, au.getName()) == null ? "Assigned." : "Alert not found.");
        return "redirect:/official/alerts";
    }

    @PostMapping("/official/observations")
    public String observe(@RequestParam Long villageId, @RequestParam String type, @RequestParam String severity,
                          @RequestParam(defaultValue = "") String note, Authentication au, RedirectAttributes ra) {
        var v = villages.findById(villageId).orElse(null);
        if (v == null || !TYPES.contains(type) || !LEVELS.contains(severity) || note.length() > 500) {
            ra.addFlashAttribute("msg", "Invalid observation."); return "redirect:/official/alerts"; }
        Observation o = new Observation(); o.villageId = v.id; o.villageName = v.name; o.type = type; o.severity = severity;
        o.note = note.trim(); o.author = au.getName(); obs.save(o);
        ra.addFlashAttribute("msg", "Observation recorded."); return "redirect:/official/alerts";
    }

    @GetMapping("/admin/users")
    public String userPage(Model m) { m.addAttribute("accounts", users.findAll()); return "users"; }

    @PostMapping("/admin/users")
    public String addUser(@RequestParam String username, @RequestParam String password, @RequestParam String role, RedirectAttributes ra) {
        String err = !username.matches("^[A-Za-z0-9_.-]{3,40}$") ? "Username must be 3-40 letters, digits, . _ -"
                : password.length() < 10 ? "Password must be at least 10 characters."
                : !List.of("ADMIN", "WORKER").contains(role) ? "Invalid role."
                : users.findByUsername(username).isPresent() ? "Username already exists." : null;
        if (err == null) { users.save(new AppUser(username, enc.encode(password), role)); audit.log("system", "USER_CREATED", username + " as " + role); }
        ra.addFlashAttribute("msg", err == null ? "Account created." : err); return "redirect:/admin/users";
    }

    @GetMapping("/official/observe")
    public String observePage(Model m) {
        m.addAttribute("areas", villages.findAll().stream().sorted(java.util.Comparator.comparing(v -> v.name)).toList());
        m.addAttribute("kinds", List.of("Unusual mosquito activity", "Suspected water contamination", "Heat-related concern", "Poor drainage or sanitation", "Other"));
        m.addAttribute("recent", obs.findTop30ByOrderByCreatedAtDesc());
        return "observe";
    }

    @PostMapping("/official/observe")
    public String submitObservation(@RequestParam Long villageId, @RequestParam String kind, @RequestParam int severity,
                                    @RequestParam(defaultValue = "") String note,
                                    org.springframework.security.core.Authentication auth, RedirectAttributes ra) {
        var v = villages.findById(villageId).orElse(null);
        var kinds = List.of("Unusual mosquito activity", "Suspected water contamination", "Heat-related concern", "Poor drainage or sanitation", "Other");
        if (v == null || !kinds.contains(kind) || severity < 1 || severity > 3 || note.length() > 500) {
            ra.addFlashAttribute("msg", "Invalid report."); return "redirect:/official/observe";
        }
        Observation o = new Observation(); o.villageId = v.id; o.villageName = v.name; o.type = kind;
        o.severity = severity == 3 ? "High" : severity == 2 ? "Moderate" : "Low"; o.note = note.trim(); o.author = auth.getName();
        obs.save(o); audit.log(auth.getName(), "FIELD_REPORT", v.name + ": " + kind + " severity " + severity);
        if (severity == 3) svc.fromField(v, o);
        ra.addFlashAttribute("msg", "Field report saved."); return "redirect:/official/observe";
    }

    @GetMapping("/admin")
    public String adminPage(Model m) {
        m.addAttribute("users", users.findAll()); m.addAttribute("audit", auditLogs.findTop50ByOrderByCreatedDesc());
        return "admin";
    }
}
