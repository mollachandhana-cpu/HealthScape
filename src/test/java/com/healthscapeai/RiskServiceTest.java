package com.healthscapeai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class RiskServiceTest {
    private final RiskService s = new RiskService();

    @Test void severeInputsAreHigh() {
        assertEquals("High", s.assess(new RiskInput(800d, 100d, 38d, 90d, 200d, 10d, 10d, 40000d, 9000d)).level());
    }
    @Test void calmInputsAreLow() {
        assertEquals("Low", s.assess(new RiskInput(0d, 0d, 25d, 40d, 30d, 95d, 95d, 40000d, 3000d)).level());
    }
}
