# BHSPL Attendance Management System

A modern, robust Attendance Management and Synchronization System for ZKTeco and compatible biometric devices. The system supports real-time synchronization, leave tracking, report generation, and employee shift management.

---

## 🗺️ System Architecture

The application is built on a hybrid communication model, allowing both local network polling and remote internet-based push integrations:

```mermaid
graph TD
    subgraph Local LAN (Head Office)
        DeviceA[Biometric Device A] <-->|UDP Port 4370<br/>Legacy Pull Mode| App[Java/Spring App]
    end

    subgraph Remote Branch (Other States)
        DeviceB[Biometric Device B] -->|HTTP Post /iclock/cdata<br/>ADMS Push Mode| Nginx[Nginx Reverse Proxy: 9002]
        Nginx -->|Proxy Pass| App
    end

    App <--> DB[(MySQL / SQLite Database)]
```

1. **Web Portal Dashboard (Port `8080` / `9002`)**: A web interface for administrators and operators to check activity logs, manage employees, track leaves, and generate reports.
2. **ADMS Push Service (Port `8081` / `9002`)**: A high-performance HTTP service listening for real-time check-in and operational logs sent by remote biometric devices.
3. **Legacy UDP Pull Sync (Port `4370`)**: A scheduled background thread that connects directly to the IP addresses of local biometric devices to download attendance logs.

---

## 🔌 Connecting Biometric Devices

Depending on your network topology, you will connect devices using one of the following two modes:

### Scenario A: Remote Network / Different States (ADMS Push Mode)

Use this when the biometric device is at a remote branch behind a router/NAT, or when the server cannot directly ping the device's IP.

#### 1. On the Biometric Device Screen:
1. Open the device menu and navigate to **Comm.** (Communication) -> **Cloud Server Settings** / **ADMS Settings** / **Web Server Settings**.
2. Configure the following properties:
   * **Server Address** (`ADMSHost`): Enter the IP address or domain name of your application server.
   * **Server Port** (`ADMSPort`): Enter the ADMS port.
     * Use **`9002`** if running behind Nginx / Docker.
     * Use **`8081`** if running the Java app directly without Nginx.
   * **Enable Cloud Server / ADMS** (`ADMSOn`): Set this to **Enabled** / **1**.
   * **HTTPS / SSL**: Set to **Disabled** (unless you configured SSL certificates on your Nginx server).
3. Save the settings and **restart the device**.

#### 2. In the Web Application Portal:
1. Find the **Serial Number (SN)** of the physical device (printed on the back of the machine or under *System Info* in the menu).
2. Go to the **Devices** page and click **Add Device**.
3. Fill in the following:
   * **Device Name**: A friendly identifier (e.g., `"Branch Exit Device"`).
   * **IP Address**: Enter **`127.0.0.1`** (Acts as a dummy placeholder since the device connects to the server, not the other way around).
   * **Port**: Leave as the default **`4370`** (Acts as a dummy placeholder).
   * **Serial Number**: Enter the **exact physical Serial Number** of the device.
   * **Location**: The physical location (e.g., `"Vijayawada Office"`).
4. Save the device. Once the device boots up and connects, the status will automatically change to **Active** and log synchronization will begin.

---

### Scenario B: Same Local LAN (Legacy UDP Pull Mode)

Use this when the server and the biometric device are on the same local area network (LAN) and can directly ping each other.

#### 1. On the Biometric Device Screen:
1. Navigate to **Comm.** -> **Ethernet** / **Network Settings**.
2. Assign the device a **Static IP address** (e.g., `192.168.1.201`) and standard Netmask/Gateway.
3. Note the default Port (typically **`4370`**).

#### 2. In the Web Application Portal:
1. Go to the **Devices** page and click **Add Device**.
2. Fill in the following:
   * **Device Name**: A friendly identifier (e.g., `"Main Entrance"`).
   * **IP Address**: Enter the **actual Static IP address** of the device (e.g., `192.168.1.201`).
   * **Port**: Enter `4370`.
   * **Serial Number**: Enter the device Serial Number.
3. Save the device. The application will pull logs periodically over UDP.

---

## 👥 Employee Synchronization & Imports

Because remote devices behind firewalls cannot accept incoming UDP connection requests from the server, importing employees varies depending on your network setup:

### Method 1: Manual Employee Setup (Recommended for Remote Devices)
1. Enroll the employee's face, fingerprint, or card on the physical biometric device.
2. Note the numeric ID assigned to the employee (e.g., `101`, `1005`).
3. Log into the Web Portal, navigate to **Employees** -> **Add Employee**.
4. Input their details (Name, Dept, Designation).
5. For **Employee ID**, type the **exact numeric ID** assigned to them on the device.
6. Click **Save**. Any past or future logs pushed by the device with that numeric ID will automatically map to this employee profile.

### Method 2: Bulk Database Import (Recommended for Large Teams)
If you have a large list of employees in Excel or CSV:
1. Connect to the database using an SQL client (like DBeaver or phpMyAdmin).
2. Insert your employee lists directly into the `employees` SQL table. 
3. Make sure the `emp_id` column contains the exact biometric Enroll IDs registered on the device.

### Method 3: Live Local Pull (Direct Network Only)
If the device is on the same local network:
1. Go to **Employees** -> click **Import from Device**.
2. Select the active device. The server will connect directly to the device's IP, download the user roster, and display a preview.
3. Select the users you wish to import, assign them names and departments, and click **Import Selected**.

---

## ⚡ Deployment & Startup

For complete setup variables, dependencies, and Docker runbooks, refer to [DEPLOYMENT.md](file:///c:/Users/user/Desktop/bhspl-attendance/DEPLOYMENT.md).

### Quick Commands

* **Run UI + Web Application locally**:
  ```cmd
  .\start_app.bat
  ```
* **Run Headless worker / background sync only**:
  ```cmd
  .\start_sync.bat
  ```
* **Start docker-compose stack (includes Nginx Reverse Proxy on Port 9002)**:
  ```bash
  docker-compose up -d
  ```
