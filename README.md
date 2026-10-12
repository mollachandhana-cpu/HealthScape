# HealthScapeAI — Environmental Health Intelligence

HealthScapeAI is a full-stack environmental-awareness platform that translates live modelled air-quality and weather data into explainable exposure insights. It is built on the existing Spring Boot + Thymeleaf + TiDB/MySQL application and retains the community locality, field observation, role-based official workspace, alert lifecycle, and audit-trail modules.

> **Safety and data integrity:** Environmental values come from Open-Meteo weather models and CAMS air-quality forecasts; they are not guaranteed to be readings from the nearest regulatory monitor. The insight rules are educational heuristics, not clinically validated risk predictions, medical advice, outbreak detection, or official warnings. Use local public-health and emergency guidance for decisions.

## What's new in this upgrade

- Redesigned responsive product dashboard plus dedicated Air Quality, Climate Risk, and Data & Methodology pages with shared navigation and a consistent visual system.
- Live geocoding via Open-Meteo; current weather/air-quality conditions and 24-hour hourly series via Open-Meteo APIs.
- Expanded environmental dimensions: US/EU AQI, PM2.5, PM10, ozone, NO₂, SO₂, CO, dust, pollen where available, heat/apparent temperature, humidity, UV, rain probability, precipitation, wind gusts, visibility and cloud cover.
- Explainable rule-based signals for pollution, heat, UV, warm/humid conditions, heavy-rain planning, strong wind, low visibility, dust and elevated modelled CO. Missing values are labelled unavailable rather than replaced with zero.
- A field-operations entry point links the existing protected community observation, sanitation/water concern and alert workflows; these are operational records, not represented as a live sensor feed.
- Optional Google Gemini-generated plain-language briefing. The app works without an API key and shows a transparent fallback when Gemini is not configured.
- Each successful environmental refresh is saved to the configured SQL database in `environmental_snapshots`; a history endpoint exposes recent saved observations for the selected area.
- Health endpoint for deploy checks, Maven tests, Docker build, Render blueprint, and documented configuration.
- The original official/field-work workflows remain in the project; the public homepage is now the environmental intelligence product.

## Technology

- Java 21, Spring Boot 3.4.4, Spring MVC, Spring Data JPA, Spring Security, Thymeleaf, Actuator
- TiDB Cloud (MySQL-compatible) or MySQL 8 for persistence
- Open-Meteo Geocoding, Forecast and Air Quality APIs (no API key required for supported non-commercial use)
- Leaflet + OpenStreetMap for map rendering
- Optional Google Gemini REST API for generated environmental explanations
- Maven, Docker, Render-compatible deployment configuration

## Run locally on macOS / VS Code with TiDB Cloud

### 1. Create or confirm the TiDB database

In TiDB Cloud SQL Editor, create the application database if needed:

```sql
CREATE DATABASE IF NOT EXISTS healthscape;
```

Confirm the TiDB cluster's public endpoint, port (typically `4000`), username, and network allowlist. Keep the database password private. If a password was previously pasted into logs or source files, rotate it in TiDB Cloud before proceeding.

### 2. Set environment variables in the terminal

Run from the project root (the directory containing `pom.xml`). Use the exact host and username shown in TiDB Cloud:

```zsh
export DATABASE_URL='jdbc:mysql://YOUR_TIDB_HOST:4000/healthscape?sslMode=VERIFY_IDENTITY'
export DATABASE_USER='YOUR_TIDB_USERNAME'
read -s 'DATABASE_PASSWORD?Enter your TiDB password: '
export DATABASE_PASSWORD
echo
export HS_ADMIN_USER='official'
export HS_ADMIN_PASSWORD='CHOOSE_A_LONG_UNIQUE_PASSWORD'
```

Optional: enable generative AI briefings with a Gemini API key from Google AI Studio. Keep it server-side and never place it in frontend JavaScript or commit it:

```zsh
export GEMINI_API_KEY='YOUR_GEMINI_API_KEY'
export GEMINI_MODEL='gemini-2.5-flash'
```

The Gemini key is optional. Without it, the dashboard's rule-based explainable insights still work. API availability and model access depend on your provider account and quotas.

The `DATABASE_URL` must be a JDBC URL beginning with `jdbc:mysql://`. Do not use the URI-style `mysql://user:password@host/...` connection string in Spring datasource configuration. Username and password are separate variables.

### 3. Start the app

Requires Java 21+ and Maven 3.9+:

```bash
java -version
mvn -version
mvn spring-boot:run
```

Open <http://localhost:8080>. Keep the terminal session open. Environment variables set in a terminal apply only to that session; re-export them in any new terminal.

The first load calls Open-Meteo. If the external API is unavailable, the app shows an error instead of silently presenting sample values as live data. Search a city, select one of the quick locations, or use browser geolocation on localhost/HTTPS.

## API routes

| Method | Route | Purpose |
|---|---|---|
| GET | `/api/environment?lat=17.385&lon=78.4867&place=Hyderabad` | Live modelled weather + air quality, hourly series and explainable insights; saves a SQL snapshot |
| GET | `/api/environment/search?q=Hyderabad` | Location geocoding |
| GET | `/air-quality` | Dedicated pollutant and air-quality view |
| GET | `/climate-risk` | Dedicated heat, UV, rainfall, wind and visibility view |
| GET | `/data-methodology` | Data provenance, model limitations and responsible-use notes |
| GET | `/api/environment/history?lat=17.385&lon=78.4867` | Recent saved snapshots near the selected coordinates |
| POST | `/api/environment/ai-brief` | Optional Gemini-generated briefing; deterministic insights remain available without a key |
| GET | `/api/villages` | Existing community locality data |
| POST | `/api/predict` | Existing rule-based risk calculator |
| GET | `/actuator/health` | Application health check for deployment |

The public `/api/environment` endpoint accepts latitude from -90 to 90 and longitude from -180 to 180. The AI endpoint is optional and should be protected with provider quota limits and deployment-level rate limiting before exposing a configured paid API key to a broad audience.

## Database notes

- Existing entities and workflows are retained. Hibernate's configured `DDL_AUTO` defaults to `update`, which creates the new `environmental_snapshots` table automatically in development.
- For a production database, prefer reviewed schema migrations (Flyway/Liquibase) over relying indefinitely on automatic DDL changes. Back up data before changing schema policy.
- `environmental_snapshots` stores coordinates, location label, modelled AQI/pollutant/weather values and capture timestamp. It is not a substitute for an official sensor archive.
- If the DB is unavailable, application startup or snapshot requests may fail. Confirm TiDB network allowlisting and TLS connection instructions before debugging application code.

## Test and package

```bash
mvn test
mvn clean package
```

Tests cover the existing risk scoring expectations and the environmental snapshot data object. Live external API calls are intentionally not required for unit tests. Before a real production release, add provider-contract/integration tests using stubbed HTTP responses, migration tests, authentication/authorization tests, rate limiting, and end-to-end browser tests.

## Docker

For a local Docker Compose run, copy `.env.example` to `.env`, set unique values, then run:

```bash
docker compose up --build
```

The compose file starts a local MySQL service and the application. This is optional; your existing TiDB Cloud workflow does not require Docker. Never commit `.env`.

## Deploy to Render with TiDB Cloud

1. Push the project to a private or public Git repository (do not include `.env`, credentials, logs, or API keys).
2. Create a Render Blueprint from the repository; `render.yaml` describes the Docker web service and `/actuator/health` health check.
3. In Render's environment settings, set `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `HS_ADMIN_USER`, and `HS_ADMIN_PASSWORD`. `DATABASE_URL` must use the JDBC URL and TLS options provided for your TiDB cluster.
4. Optionally set `GEMINI_API_KEY` and `GEMINI_MODEL` to enable generated briefings.
5. Ensure the TiDB endpoint permits connections from the deployment environment and follow TiDB's current TLS/network guidance. Do not weaken TLS as a workaround.
6. Deploy, wait for the health check, and verify the dashboard plus `/api/environment` and `/actuator/health`.

Free hosting instances may sleep, and external APIs/database connections may have quotas or network restrictions. Validate current provider terms and limits before public/commercial use.

## Attribution and external services

- Open-Meteo weather and geocoding: <https://open-meteo.com/>; weather forecasts: <https://open-meteo.com/en/docs>; air quality: <https://open-meteo.com/en/docs/air-quality-api>.
- Map tiles and map data: © OpenStreetMap contributors, <https://www.openstreetmap.org/copyright>; Leaflet: <https://leafletjs.com/>.
- Optional generative explanations: Google Gemini API, <https://ai.google.dev/api>.

## Project layout

- `src/main/java/com/healthscapeai/EnvironmentalIntelligenceService.java` — live data aggregation and transparent environmental rules.
- `EnvironmentalApiController.java` — public environmental API and snapshot history.
- `EnvironmentalSnapshot.java` / `EnvironmentalSnapshotRepository.java` — SQL persistence for fetched observations.
- `AiBriefService.java` — optional Gemini API integration.
- `src/main/resources/templates/index.html` — dashboard layout.
- `src/main/resources/static/css/app.css` — responsive visual system.
- `src/main/resources/static/js/app.js` — dashboard interactions, map, charts, search and AI briefing.
- Existing official workflows, alerts, audit trail and risk calculator remain in their original classes/templates.

## Current limitations (documented, not hidden)

- AQI/pollutants are model estimates from provider grids and can differ from nearby stations.
- Current insight thresholds are explainable heuristic triggers, not a validated clinical/public-health model.
- The 24-hour graph displays forecast values. The SQL history count grows only as users refresh the selected locations; it is not historical coverage before the app was used.
- Gemini is optional; no model-generated text is claimed unless a server-side API key is configured and a successful response is received.
- Production readiness still requires deployment-specific secrets, schema migrations, provider quotas/rate limiting, operational monitoring, security review and integration/end-to-end tests.

## Area Intelligence public experience

The `/area-intelligence` page provides a public, locality-level briefing with:
- Area selection from the seeded locality registry
- A visible distinction between recent area updates and stale/unverified records
- Rule-based risk contributors and precautions only when the area record is considered fresh
- An evidence register for AQI, rainfall, water-quality score, sanitation score and aggregated health-report count
- Recent field observations and unresolved response-item counts
- Separate next-step guidance for residents and operational review for authorized officers

**Important limitations:** the existing risk score is a demonstration heuristic, not a clinically validated disease forecast. Seeded area values are not live observations. The UI deliberately labels stale records as not assessed and explains that recorded values need verification. No SMS provider, validated disease-specific ML model, automated outbreak detection, or full Telugu translation is claimed by this page. Those require provider credentials, suitable historical data, evaluation and further implementation.

## Progressive Web App (PWA) and offline behavior

The dashboard can be installed as a web app on supported browsers when served from `localhost` or HTTPS. The service worker caches the static application shell and shows a clear offline page when navigation cannot reach the server. It deliberately does **not** cache `/api/**` responses, so stale air-quality or weather readings are never presented as current. Dynamic environmental data still requires a working network and backend.

To test locally:

1. Start the app with the environment variables configured as described above.
2. Open `http://localhost:8080` in a supported browser.
3. Open Developer Tools → Application → Manifest / Service Workers to inspect registration.
4. Use the browser's install option if available. To test the offline page, load the dashboard once, switch the browser to offline mode, and navigate/reload. API data should remain unavailable rather than being misrepresented as live.

After changing cached CSS or JavaScript, increment the `CACHE` version in `src/main/resources/static/sw.js` so clients refresh their application shell.
