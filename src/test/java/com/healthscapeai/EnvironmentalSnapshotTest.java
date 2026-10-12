package com.healthscapeai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentalSnapshotTest {
    @Test void snapshotStoresModelledEnvironmentalValues() {
        EnvironmentalSnapshot s = new EnvironmentalSnapshot(17.385,78.4867,"Hyderabad",82,23.4,41.2,34.1,61,7.5);
        assertEquals(17.385,s.latitude,0.0001);
        assertEquals("Hyderabad",s.place);
        assertEquals(82,s.usAqi,0.0001);
        assertEquals(23.4,s.pm25,0.0001);
        assertNotNull(s.capturedAt);
    }
}
