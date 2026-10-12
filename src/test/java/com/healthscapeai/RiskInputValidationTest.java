package com.healthscapeai;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskInputValidationTest {
    private static Validator validator;
    private static jakarta.validation.ValidatorFactory factory;

    @BeforeAll static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void tearDown() { factory.close(); }

    @Test void acceptsPlausibleInputs() {
        RiskInput input = new RiskInput(10d, 12d, 31d, 60d, 85d, 75d, 80d, 10000d, 2500d);
        assertTrue(validator.validate(input).isEmpty());
    }

    @Test void rejectsHumidityOutsidePercentageRange() {
        RiskInput input = new RiskInput(10d, 12d, 31d, 140d, 85d, 75d, 80d, 10000d, 2500d);
        assertTrue(validator.validate(input).stream().anyMatch(v -> v.getPropertyPath().toString().equals("humidity")));
    }

    @Test void rejectsNegativePopulationAndImpossibleTemperature() {
        RiskInput input = new RiskInput(10d, 12d, 150d, 60d, 85d, 75d, 80d, -1d, 2500d);
        var fields = validator.validate(input).stream().map(v -> v.getPropertyPath().toString()).toList();
        assertTrue(fields.contains("temperature"));
        assertTrue(fields.contains("population"));
    }
}
