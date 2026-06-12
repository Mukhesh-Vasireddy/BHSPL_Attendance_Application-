# BAVYA Attendance Management System

BAVYA Attendance Management System is a professional, high-performance attendance and workforce tracking solution designed for corporate environments. It interfaces seamlessly with biometric devices (ZKTeco compatible) in real-time, processes logs, manages leave policies, and generates analytics.

---

## 🚀 Key Features

*   **Real-time Biometric Synchronization**: Support for ADMS Push protocol and active UDP polling client configuration.
*   **Workforce Scheduling**: Shift mapping supporting General, morning, night shifts, and flexible weekly offs.
*   **Leave Management Engine**: Comprehensive policy engine with automated yearly leave credits and pro-rata options.
*   **Dual UI/Headless Architecture**: Contains a modern desktop Swing UI with enterprise-grade look and feel, an embedded Spring Boot Web Portal for browser dashboard analytics, and a CLI Headless Sync Worker.
*   **Detailed Reporting**: Export daily logs, leaves, exceptions, monthly sheets, and raw device audits to Excel or CSV.

---

## 🛠 Tech Stack

*   **Backend & APIs**: Java 17, Spring Boot 3.2.4 (Thymeleaf, Embedded Tomcat)
*   **Desktop Client**: Java Swing (FlatLaf Enterprise Look & Feel, MigLayout)
*   **Database**: MySQL 8.0
*   **Biometric Protcol**: ZKTeco SDK (UDP socket protocol) and ADMS Push Protocol handler
*   **Build Tool**: Apache Maven

---

## 📁 Repository Layout

```text
bhspl-attendance/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/bhspl/
│   │   │       ├── core/       # Configurations loaders
│   │   │       ├── db/         # JDBC Connection, migrations & seeders
│   │   │       ├── model/      # Database models
│   │   │       ├── service/    # ADMS Listener and Background polling service
│   │   │       ├── ui/         # Swing Frames & Panels (Desktop client)
│   │   │       ├── util/       # Biometric communication protocol, exporters, metrics
│   │   │       └── web/        # Spring Boot web portal controller & routing config
│   │   └── resources/
│   │       ├── static/         # Frontend web assets (JS, CSS, SVGs)
│   │       ├── templates/      # Thymeleaf web page layouts
│   │       └── enterprise.properties  # FlatLaf Enterprise theme properties
├── Dockerfile                  # Multi-stage Docker deployment config
├── DEPLOYMENT.md               # Environment configs and installation steps
├── start_app.bat               # Starts complete system (Desktop GUI + Web Server)
└── start_sync.bat              # Starts headless background worker
```

---

## 📋 Prerequisites

- **Java**: JDK 17 installed
- **Database**: Active MySQL instance running
- **Network**: Biometric devices must be pingable on UDP port `4370` (polling mode) or configured to push data on HTTP port `8081` (push mode)

---

## ⚙️ Configuration & Database Setup

1. **MySQL Database**:
   Create a database on your MySQL server:
   ```sql
   CREATE DATABASE `bhspl_attendance` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. **Database Settings**:
   Database details can be configured via environment variables (recommended for production):
   - `DB_HOST`: Database server IP
   - `DB_PORT`: Database port (default `3306`)
   - `DB_NAME`: Database name (`bhspl_attendance`)
   - `DB_USER`: Username (default `root`)
   - `DB_PASSWORD`: User password

   Alternatively, settings can be input during the GUI launch setup or written directly to `%LOCALAPPDATA%/BHSPL_Attendance_Java/bhspl_config.ini`.

---

## 🏃 Running the Application

### Option 1: Desktop GUI & Web Portal
Use the Maven wrapper to build and start the complete application:
```cmd
.\mvnw.cmd compile exec:java -Dexec.mainClass="com.bhspl.Main"
```
Or execute the local shortcut:
```cmd
.\start_app.bat
```

### Option 2: Headless Synchronization Worker
To compile dependencies and execute the daemon CLI worker:
```cmd
.\start_sync.bat
```

---

## 📦 Building & Production Packaging

Generate a self-contained executable JAR (fat-jar):
```cmd
.\mvnw.cmd clean package -DskipTests
```
The compiled archive is generated under `target/bhspl-attendance-1.0-SNAPSHOT.jar`. Run it headlessly on production servers:
```bash
java -Djava.awt.headless=true -jar target/bhspl-attendance-1.0-SNAPSHOT.jar
```

---

## 📖 Additional Resources

*   For environment variable references, docker build details, and troubleshooting notes, check **[DEPLOYMENT.md](file:///c:/Users/user/Downloads/Backup-New/Backup-New/bhspl-attendance/DEPLOYMENT.md)**.
*   License: Proprietary - Bavya Health Services Pvt. Ltd. (BHSPL)
