package com.healthscapeai;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AiBriefServiceTest {
    @Test void generativeBriefingIsOptionalAndFallbackIsExplicit() {
        AiBriefService service = new AiBriefService();
        Map<String,Object> result = service.explain(Map.of());
        assertEquals(false, result.get("enabled"));
        assertEquals("rule-based", result.get("provider"));
        assertTrue(result.get("message").toString().contains("not configured"));
    }
}
