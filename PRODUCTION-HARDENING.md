# HealthScapeAI — production-hardening pass

This pass tightens the input boundary for the public risk calculator and adds regression tests for common invalid inputs. It also explicitly configures browser security headers (MIME sniffing protection, frame denial, and HSTS).

## Validation added

- Non-negative and bounded case counts, rainfall, and AQI.
- Plausible temperature range (-80°C to 70°C).
- Percentage fields constrained to 0–100.
- Positive, bounded population and population density.
- Tests cover a valid request, invalid humidity, impossible temperature, and negative population.

These are software input guardrails, not medical validation of the risk model. The risk score remains a demonstration score and must not be treated as diagnosis or official public-health guidance. Review the bounds against the units and data contracts of your real data sources before production.

## Before public deployment

1. Set `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD` in the host's secret manager; never commit credentials.
2. Set `DDL_AUTO=validate` after applying reviewed database migrations. The current development default `update` is not a migration strategy.
3. Create a strong admin password and verify the initial-user provisioning flow before exposing official/admin pages.
4. Run `mvn --batch-mode clean test` and `mvn --batch-mode package`.
5. Deploy behind HTTPS. HSTS is configured; only enable it when HTTPS is correctly terminated at the host/proxy.
6. Test every protected route with anonymous, worker, and admin accounts; test CSRF on all browser form submissions.
7. Confirm external API limits, failure handling, retention policy, and privacy disclosures for stored environmental snapshots.

## Verification status

The source changes and tests are included in this ZIP. Run the Maven commands in your environment to confirm dependency resolution and execute the tests; a successful startup alone does not prove production readiness.
