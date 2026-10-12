-- Run this in TiDB Cloud SQL Editor before starting the application.
-- Use a dedicated schema; do not use the built-in `sys` schema for app tables.
CREATE DATABASE IF NOT EXISTS healthscape;
USE healthscape;
-- Spring Data JPA creates/updates the application's tables on startup in demo mode.
