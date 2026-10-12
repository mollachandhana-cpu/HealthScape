# Release notes — Deployment Readiness

- Container now runs as a non-root user.
- Docker build excludes local secrets, IDE files, and build output.
- GitHub Actions runs Maven tests, packages the application, and validates the Docker build.
- Added a deployment playbook for Render/TiDB environment configuration, health checks, and production gates.

Validation performed when assembling this release: ZIP source was extracted and edited; workflow and Dockerfile were inspected. Maven tests, Docker image build, and live deployment still need to be run on a machine with the required tooling and configuration.
