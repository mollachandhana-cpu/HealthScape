package com.healthscapeai;

import org.springframework.stereotype.Service;
import java.util.*;

/** Transparent rule-based demonstration score. Not clinically validated. */
@Service
public class RiskService {
    public record Factor(String feature, double impact) {}
    public record Result(double score, String level, List<Factor> factors) {}

    public Result assess(RiskInput d) {
        Map<String, Double> c = new LinkedHashMap<>();
        c.put("Case count", clamp(d.caseCount() / Math.max(1, d.population() * 0.02) * 100) * .20);
        c.put("Rainfall", clamp(d.rainfall()) * .10);
        c.put("Temperature", (d.temperature() >= 35 ? 100 : d.temperature() >= 30 ? 60 : 20) * .10);
        c.put("Humidity", (d.humidity() >= 80 ? 100 : d.humidity() >= 60 ? 60 : 20) * .10);
        c.put("Air quality (AQI)", (d.aqi() < 50 ? 20 : d.aqi() < 100 ? 45 : d.aqi() < 150 ? 70 : 100) * .15);
        c.put("Water quality", (100 - d.waterQuality()) * .15);
        c.put("Sanitation", (100 - d.sanitation()) * .10);
        c.put("Population density", clamp(d.populationDensity() / 100) * .10);
        double score = c.values().stream().mapToDouble(Double::doubleValue).sum();
        String level = score < 35 ? "Low" : score < 65 ? "Medium" : "High";
        List<Factor> factors = c.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(5)
                .map(e -> new Factor(e.getKey(), Math.round(e.getValue() * 10) / 10.0)).toList();
        return new Result(Math.round(score * 10) / 10.0, level, factors);
    }

    public List<String> precautions(String level, List<Factor> f) {
        var out = new LinkedHashSet<String>();
        for (var x : f.subList(0, Math.min(3, f.size()))) {
            switch (x.feature()) {
                case "Rainfall", "Humidity" -> out.add("Empty stagnant water around your home weekly; use mosquito nets or repellent.");
                case "Air quality (AQI)" -> out.add("Limit outdoor exertion on poor-air days; children and elders should wear a mask outdoors.");
                case "Temperature" -> out.add("Drink water often, avoid outdoor work from 12 to 4 pm, and watch for heat exhaustion.");
                case "Water quality", "Sanitation" -> out.add("Boil or filter drinking water and wash hands with soap.");
                case "Case count" -> out.add("See a doctor early for persistent fever and follow local health advisories.");
                default -> {}
            }
        }
        if (!level.equals("Low")) out.add("Follow advisories from your local health officials.");
        if (out.isEmpty()) out.add("Conditions look normal. Keep surroundings clean and stay informed.");
        return List.copyOf(out);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }
}
