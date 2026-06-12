# BAVYA Attendance Management System - Deployment Guide

This guide describes how to deploy and configure the Attendance Management System in local development, headless server, and containerized production environments.

---

## 1. Prerequisites
- **Java Runtime**: JDK 17 (or JRE 17)
- **Build Tool**: Apache Maven 3.9+
- **Database**: MySQL 8.0+
- **Port requirements**:
  - `8080` (Default) - Web Portal dashboard UI
  - `8081` (Default) - ADMS Push biometric device communication

---

## 2. Database Setup

1. **Create Database schema**:
   Log into your MySQL instance and run:
   ```sql
   CREATE DATABASE IF NOT EXISTS `bhspl_attendance` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. **Schema and Data Seeding**:
   The application is self-healing. Tables, default indexes, seed configurations (default departments, designations, shifts, and leave policies), and an initial admin account (`admin` / `admin123`) are automatically created and updated on startup.

---

## 3. Environment Variables

The application can be configured dynamically via environment variables, which override local configuration files (`bhspl_config.ini` or `bhspl_config.properties`).

| Environment Variable | Description | Default Value |
| :--- | :--- | :--- |
| `DB_HOST` | Database host address | (Reads from INI/Properties if unset) |
| `DB_PORT` | Database port number | `3306` |
| `DB_NAME` | Database schema name | `bhspl_attendance` |
| `DB_USER` | Database username | `root` |
| `DB_PASSWORD` | Database user password | (Empty) |
| `ADMS_PORT` | ADMS Push Listener server port | `8081` |
| `PORT` | Spring Boot Web Portal server port | `8080` |
| `SPRING_LOG_LEVEL` | Framework logging verbosity | `INFO` |
| `APP_LOG_LEVEL` | Application logging verbosity | `INFO` |

---

## 4. Local Deployment

### Option A: Complete Web & UI App (Swing + Spring Boot)
Run the bat script:
```cmd
.\start_app.bat
```
Or run manually with Maven:
```cmd
mvn clean compile exec:java -Dexec.mainClass="com.bhspl.Main"
```

### Option B: Headless Background Sync Worker (CLI Mode)
If running on a headless server or in a command line window without UI:
Run the background worker script:
```cmd
.\start_sync.bat
```
Or run manually with JVM classpath:
```cmd
mvn clean compile dependency:copy-dependencies
java -cp "target/classes;target/lib/*" com.bhspl.SyncWorker
```

---

## 5. Server Deployment

### Building the Executable JAR
Package the application using Maven:
```cmd
mvn clean package -DskipTests
```
This produces a fat executable jar: `target/bhspl-attendance-1.0-SNAPSHOT.jar` containing all Spring Boot dependencies and the embedded Tomcat server.

### Running Headless Executable JAR
To run the server in a headless linux/unix environment (no Swing graphical interfaces initialized):
```bash
java -Djava.awt.headless=true -jar target/bhspl-attendance-1.0-SNAPSHOT.jar
```

---

## 6. Docker Deployment

Build the multi-stage Docker image:
```bash
docker build -t bhspl-attendance:latest .
```

Run the container mapping the ports and specifying connection details:
```bash
docker run -d \
  -p 8080:8080 \
  -p 8081:8081 \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=3306 \
  -e DB_NAME=bhspl_attendance \
  -e DB_USER=root \
  -e DB_PASSWORD=secretpassword \
  -e ADMS_PORT=8081 \
  --name bhspl-attendance-app \
  bhspl-attendance:latest
```

---

## 7. Troubleshooting

* **Swing Graphics Failure (HeadlessException)**:
  If running on servers or Docker, guarantee that the `-Djava.awt.headless=true` JVM flag is present in your execution script.
* **Database Lock Contention**:
  Avoid running multi-threaded parallel synchronizations while executing heavy historical log scans. If the web UI becomes unresponsive, wait a minute for sync transactions to commit.
* **Port Conflicts**:
  If Port `8081` (ADMS) is busy, set `ADMS_PORT` env variable to another available port. Make sure to update your biometric devices ADMS setup configuration accordingly.
