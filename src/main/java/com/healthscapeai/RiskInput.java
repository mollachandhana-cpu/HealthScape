package com.healthscapeai;

import jakarta.validation.constraints.*;

/** Input limits keep malformed or implausible values from distorting the demo risk score. */
public record RiskInput(
        @NotNull @DecimalMin("0.0") @DecimalMax("100000000.0") Double caseCount,
        @NotNull @DecimalMin("0.0") @DecimalMax("1000.0") Double rainfall,
        @NotNull @DecimalMin("-80.0") @DecimalMax("70.0") Double temperature,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double humidity,
        @NotNull @DecimalMin("0.0") @DecimalMax("1000.0") Double aqi,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double waterQuality,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double sanitation,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("10000000000.0") Double population,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("100000000.0") Double populationDensity) {}
