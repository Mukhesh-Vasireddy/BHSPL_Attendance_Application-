

# BHSPL Attendance Management System

Enterprise Biometric Attendance & Workforce Management Platform

Production-ready attendance management system for centralized biometric attendance, employee management, shift scheduling, leave management, and real-time device synchronization across multiple locations.# BHSPL Attendance Management System


# Overview

The **BHSPL Attendance Management System** is an enterprise-grade biometric attendance platform designed to manage workforce attendance across multiple locations. The application integrates seamlessly with ZKTeco biometric devices and supports both **LAN-based synchronization** and **Internet-based ADMS communication**, making it suitable for organizations with local offices as well as geographically distributed branches.

The platform provides centralized employee management, shift scheduling, leave management, real-time attendance synchronization, audit logging, and comprehensive reporting through a modern web-based administration portal.

---

# Key Features

## Attendance Management

- Real-time attendance synchronization
- Automatic IN/OUT punch processing
- Missing punch detection
- Attendance recalculation
- Historical attendance rebuild
- Duplicate punch filtering

## Device Integration

- ZKTeco ADMS support
- UDP Pull synchronization
- HTTP Push (ADMS) synchronization
- Device heartbeat monitoring
- Multi-device management
- Automatic device status updates

## Employee Management

- Employee registration
- Department management
- Designation management
- Employee-device mapping
- Bulk employee import
- Device user synchronization

## Shift & Leave Management

- General, Day, and Night shifts
- Weekly off management
- Shift assignment
- Leave categories
- Leave application workflow
- Holiday calendar management

## Reports

Generate reports including:

- Daily Attendance
- Monthly Attendance
- Employee Summary
- Missing Punch Report
- Late Login Report
- Early Logout Report
- Leave Report
- Overtime Report
- Device Synchronization Report

Export supported formats:

- Excel (.xls)
- CSV
- Printable Reports

---

# System Architecture

```text
                    Internet
                        │
                        │
                ADMS HTTP Push
                     Port 9002
                        │
                 Nginx Reverse Proxy
                        │
          ┌─────────────┴─────────────┐
          │                           │
          │                           │
     Spring Boot UI             ADMS Service
          │                           │
          └─────────────┬─────────────┘
                        │
               Attendance Engine
                        │
               Employee Management
                        │
               Shift Management
                        │
                Reporting Engine
                        │
                  MySQL Database
```

---

# Communication Modes

## ADMS Push Mode

Recommended for:

- Remote Offices
- Branch Locations
- Internet-connected Devices
- Cloud Deployment

Configuration:

| Setting | Value |
|----------|-------|
| Server Address | Public IP / Domain |
| Server Port | 9002 |
| ADMS | Enabled |

---

## UDP Pull Mode

Recommended for:

- Local Office
- Same LAN
- Static IP Devices

Configuration:

| Setting | Value |
|----------|-------|
| Device IP | Static LAN IP |
| Device Port | 4370 |

---

# Employee Synchronization

Attendance logs contain only the biometric **Enroll ID**.

Employees can be synchronized using:

- Manual Registration
- SQL Bulk Import
- Import From Device

**Note:** Employee ID in the application must match the Enroll ID configured on the biometric device.

---

# Port Configuration

| Port | Protocol | Purpose |
|------|----------|---------|
| 9002 | HTTP | ADMS Device Communication |
| 8080 | HTTP | Web Application |
| 8081 | HTTP | ADMS REST Service |
| 4370 | UDP | Device Polling |

---

# Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 17 |
| Framework | Spring Boot 3 |
| Security | Spring Security |
| Database | MySQL |
| ORM | Spring Data JPA |
| Frontend | Thymeleaf |
| Build Tool | Maven |
| Reverse Proxy | Nginx |
| Deployment | Docker |
| Containerization | Docker Compose |
| Logging | SLF4J + Logback |

---

# Project Structure

```
src
├── controller
├── service
├── repository
├── entity
├── dto
├── config
├── scheduler
├── security
├── util
├── websocket
└── resources

docker/
nginx/
scripts/
database/
logs/
```

---

# Installation

Clone the repository

```bash
git clone https://github.com/your-company/bhspl-attendance.git
```

Build the project

```bash
mvn clean install
```

Run using Docker

```bash
docker-compose up -d
```

Run locally

```cmd
start_app.bat
```

Run synchronization service

```cmd
start_sync.bat
```

---

# Supported Devices

- ZKTeco P4
- ZKTeco F18
- ZKTeco MB20
- ZKTeco K40
- ZKTeco iClock Series
- ZKTeco SpeedFace Series
- ADMS Compatible Devices

---

# Security

- Authentication
- Role-Based Access Control (RBAC)
- Activity Logging
- Audit Trail
- Password Encryption
- Device Authentication
- Session Management

---

# Performance

The application is optimized for enterprise deployments with:

- Real-time attendance synchronization
- High concurrency support
- Multi-device synchronization
- Automatic retry mechanism
- Optimized database queries
- Background scheduled services

---

# Deployment Modes

| Mode | Description |
|------|-------------|
| Local Office | UDP Pull Mode |
| Remote Branch | ADMS Push Mode |
| Hybrid | LAN + WAN |
| Docker | Production Deployment |
| Standalone | Development |

---

# Future Enhancements

- Face Recognition Integration
- Mobile Attendance
- GPS Attendance
- REST API Integrations
- Dashboard Analytics
- Notification Service
- ERP Integration
- AI-based Attendance Insights

---

# License

**Proprietary Software**

Copyright © BHSPL.

This software is proprietary and confidential. Unauthorized copying, distribution, modification, or reverse engineering is strictly prohibited.

---

# Developed By

**BHSPL Development Team**

Enterprise Attendance & Workforce Management Platform

---

**Version:** 1.0.0

**Status:** Production Ready
