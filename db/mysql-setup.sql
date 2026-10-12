-- Run once as the MySQL root user:  mysql -u root -p < db/mysql-setup.sql
-- Replace the password placeholder below before running, and use the same value in DATABASE_PASSWORD.
CREATE DATABASE IF NOT EXISTS healthscape CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'healthscape'@'%' IDENTIFIED BY 'REPLACE_WITH_A_LONG_UNIQUE_PASSWORD';
GRANT ALL PRIVILEGES ON healthscape.* TO 'healthscape'@'%';
FLUSH PRIVILEGES;

USE healthscape;

-- Optional: the app creates these tables itself (ddl-auto=update). This is the same schema for manual setup.
CREATE TABLE IF NOT EXISTS village (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  zone VARCHAR(30),
  source VARCHAR(60),
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  temperature DOUBLE NOT NULL DEFAULT 30,
  humidity DOUBLE NOT NULL DEFAULT 60,
  rainfall DOUBLE NOT NULL DEFAULT 0,
  aqi DOUBLE NOT NULL DEFAULT 60,
  water_quality DOUBLE NOT NULL DEFAULT 75,
  sanitation DOUBLE NOT NULL DEFAULT 75,
  population DOUBLE NOT NULL DEFAULT 40000,
  population_density DOUBLE NOT NULL DEFAULT 6000,
  case_count DOUBLE NOT NULL DEFAULT 0,
  updated DATETIME(6) NULL,
  UNIQUE KEY uq_village_name (name)
);

CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL   -- BCrypt hash, never plain text
);

-- Useful checks
-- SELECT name, zone, temperature, aqi, source, updated FROM village ORDER BY name;
-- SELECT username FROM app_user;
