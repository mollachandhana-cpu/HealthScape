# HealthScapeAI Deployment Playbook

This release adds a non-root runtime container and CI checks for Maven tests, packaging, and Docker image construction. It does not deploy automatically or certify production readiness.

## 1. Local verification

```bash
mvn --batch-mode clean test
mvn --batch-mode package
docker build -t healthscapeai:local .
```

Run the container only after setting the environment variables in your shell or deployment platform. Never commit `.env` or database credentials.

## 2. Required environment variables

- `DATABASE_URL`: full JDBC URL for your MySQL-compatible database, including TLS settings required by your provider.
- `DATABASE_USER`: database username.
- `DATABASE_PASSWORD`: database password.
- `HS_ADMIN_USER` and `HS_ADMIN_PASSWORD`: configure only if the application’s current admin bootstrap expects them.
- `DDL_AUTO`: keep `update` only for development while schema migrations are not yet introduced. Before production, adopt a reviewed migration process (for example Flyway) and change Hibernate to `validate` after the existing schema is baselined.

Do not paste secrets into chat, source code, Git commits, build logs, or screenshots.

## 3. Render deployment outline

1. Push the project to a private GitHub repository.
2. In Render, create a Web Service from that repository and choose Docker.
3. Add the database variables as secret environment variables in Render; do not commit values to `render.yaml`.
4. Set the health-check path to `/actuator/health`.
5. Deploy, then inspect build logs and runtime logs before opening the public URL.
6. Confirm the health endpoint, sign-in, dashboard, database-backed pages, and failure behavior for unavailable upstream APIs.

A successful container build does not prove that credentials, database networking, schema, or login flows work in the deployed environment.

## 4. Before public production use

- Rotate any credential that has ever been exposed in a terminal screenshot or chat.
- Use a least-privilege database account and verify TLS certificate validation.
- Introduce versioned database migrations and test restore from backup.
- Verify authorization for every admin/write route; do not rely only on hidden UI links.
- Add rate limiting and abuse protection to public forms and AI endpoints.
- Review privacy, retention, and consent for field observations and audit data.
- Show timestamps, source, freshness, and uncertainty for environmental data; do not present estimates as official medical advice.
- Run dependency/security scans and test with production-like configuration.

## 5. Troubleshooting

- `Access denied`: verify database user, password, and allowed database privileges; re-enter the secret without printing it.
- Connection timeout: verify host, port, outbound networking, and provider allowlists.
- Health check fails: inspect startup logs and verify `/actuator/health` is reachable.
- Container starts but page fails: check runtime logs, template errors, datasource settings, and required environment variables.
- CI test fails: reproduce locally with `mvn --batch-mode clean test` and fix the first relevant error before packaging.
