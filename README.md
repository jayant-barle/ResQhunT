# ResQhunT — Smart Offline Emergency Communication & Disaster Rescue Network

> **Tagline:** *"When Networks Fail, ResQhunT Connects."*

ResQhunT is an offline-first disaster response and emergency communication system. When telecommunication towers, cell reception, and internet infrastructure fail during earthquakes, floods, or cyclones, ResQhunT turns ordinary Android smartphones into an ad-hoc emergency communication mesh using Google Nearby Connections API over Bluetooth Low Energy (BLE) and Wi-Fi Direct.

---

## 1. System Architecture

```
+-----------------------------------------------------------------------------------+
|                            CITIZEN ANDROID APPLICATION                            |
|                                                                                   |
|  [ Jetpack Compose UI ] <---> [ Room SQLite DB ] <---> [ Store-and-Forward Engine]|
|                                (Single Source of Truth)              |            |
+----------------------------------------------------------------------|------------+
                                                                       |
                            Google Nearby Connections (BLE / P2P)      v
+-----------------------------------------------------------------------------------+
|                            RELAY ANDROID DEVICE (PEER)                            |
|                                                                                   |
|  [ Nearby Adapter ] <---> [ Envelope Validation & Dedup ] <---> [ Relay Storage ] |
+----------------------------------------------------------------------|------------+
                                                                       |
                                                Cellular / Wi-Fi Sync  v
+-----------------------------------------------------------------------------------+
|                            RESQHUNT BACKEND CLOUD SERVICE                         |
|                                                                                   |
|  [ Express API Gateway ] <---> [ Idempotency & Auth ] <---> [ Prisma ORM ]        |
|  [ Priority Engine ]                                              |               |
|                                                                   v               |
|                                                       [ PostgreSQL / Supabase ]   |
+-------------------------------------------------------------------|---------------+
                                                                    |
                                                  WebSocket / REST  v
+-----------------------------------------------------------------------------------+
|                            WEB RESCUE COMMAND DASHBOARD                           |
|                                                                                   |
|  [ React / Vite UI ]  <---> [ Leaflet Live Map ] <---> [ Coordinator Triage ]     |
|  [ Volunteer Dispatch ]     [ Supply Allocation ]       [ Real-time Activity Log] |
+-----------------------------------------------------------------------------------+
```

---

## 2. Directory Structure

```
Hackbios/
├── android/            # Native Android Application (Kotlin, Jetpack Compose, Room, Nearby API)
│   ├── app/build/outputs/apk/debug/app-debug.apk   # Pre-built runnable debug APK
│   └── app/src/main/   # Complete native source code
├── backend/            # Express, TypeScript, Prisma ORM, PostgreSQL & SQLite fallback
│   ├── src/            # Controllers, Services, Priority Engine, Idempotency Sync
│   └── prisma/         # Prisma Schema & Deterministic Seed Data
├── web/                # Web Rescue Command Dashboard (React, Vite, Tailwind, Leaflet)
│   ├── src/            # Triage pages, Leaflet Map, Volunteer & Supply Management
│   └── dist/           # Pre-built production bundle
└── docs/               # Technical documentation
    ├── architecture.md
    ├── development-plan.md
    ├── api-contracts.md
    ├── testing-plan.md
    ├── known-limitations.md
    └── demo-script.md
```

---

## 3. Quick Start & Execution Guide

### Prerequisites
- Node.js `v18+` (Tested on `v24.19.0`)
- Android Studio / Android SDK (API 34) & JDK 17/21

---

### Step 1: Run the Backend Service

```bash
cd backend

# Install dependencies (already completed in workspace)
npm install

# Push database schema & generate Prisma Client (SQLite local default)
npx prisma db push

# Seed deterministic demonstration disaster scenario
npm run prisma:seed

# Run automated backend test suite (Auth, Idempotency, Priority, Inventory)
npm test

# Start the development server
npm run dev
# Backend is online at http://localhost:5000
```

---

### Step 2: Run the Web Rescue Command Dashboard

```bash
cd web

# Install dependencies
npm install

# Verify production build & TypeScript typecheck
npm run build

# Start the Vite development server
npm run dev
# Web Dashboard is accessible at http://localhost:5173
```

#### Demo User Credentials:
- **Chief Coordinator:** `coordinator@resqhunt.org` / `Password123!`
- **Paramedic Volunteer:** `volunteer1@resqhunt.org` / `Password123!`
- **Search & Rescue Volunteer:** `volunteer2@resqhunt.org` / `Password123!`
- **Citizen:** `citizen@resqhunt.org` / `Password123!`

*(The dashboard also includes instant one-click login buttons for evaluators).*

---

### Step 3: Run / Build the Native Android Application

```bash
cd android

# Run Android unit test suite (Protocol Envelope, Checksum, Priority Engine, Deduplication)
powershell -Command "[System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Android\Android Studio\jbr', 'Process'); .\gradlew.bat test"

# Build Debug APK
powershell -Command "[System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Android\Android Studio\jbr', 'Process'); .\gradlew.bat assembleDebug"

# Install on a connected physical device or emulator:
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Physical Device Verification Protocol (Multi-Hop Mesh)

To verify the store-and-forward mesh physically across 3 devices without cellular data or Wi-Fi routers:
1. **Device A (Victim)**: Airplane mode ON, Bluetooth ON. Trigger **One-Tap SOS** (`MEDICAL` + `CRITICAL`). Accidental activation countdown completes. Request saved to Room SQLite as `STORED_LOCALLY`.
2. **Device B (Relay)**: Airplane mode ON, Bluetooth ON. ResQhunT automatically discovers Device A via Google Nearby Connections (`Strategy.P2P_CLUSTER`). Serialized JSON envelope with SHA-256 integrity hash transfers. Device A status updates to `RELAYED_TO_PEER`. Device B buffers request with incremented hop count (`2/5`).
3. **Device C (Gateway)**: Connected to internet / Wi-Fi. Receives envelope from Device B. Automatically uploads via `POST /api/sos/sync`.
4. **Dashboard**: Incident appears in real-time on Web Rescue Command Center. Deterministic Priority Engine assigns score (e.g. `92.5/100`), coordinates appear on Leaflet map, and Coordinator acknowledges and assigns rescue volunteers.

---

## 5. Security & Safety Design
- **Local Persistence Guarantee**: Emergencies are committed to Room SQLite before any network or BLE radio transmission is initiated.
- **Accidental Activation Prevention**: A 5-second cancel countdown modal prevents accidental false alarms while preserving zero-delay emergency dispatch.
- **Deduplication & Loop Prevention**: Unique `messageId` indexing and hop count thresholds (`maxHops = 5`) prevent broadcast storms and infinite routing loops.
- **Data Minimization**: Broadcast payloads omit private passwords or tokens; only incident severity, category, victim count, coordinates, and short description are relayed.
- **Inventory Bounds**: Relief supply allocations utilize database transactions preventing negative inventory stock.
