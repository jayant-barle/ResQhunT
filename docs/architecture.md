# ResQhunT Architecture Specification

## 1. System Overview
ResQhunT is a mission-critical, offline-first disaster response and emergency communication system. When telecommunication towers, cell reception, and internet infrastructure fail during earthquakes, floods, or cyclones, ResQhunT creates an ad-hoc device-to-device mesh network using Google Nearby Connections API over Bluetooth Low Energy (BLE) and Wi-Fi Direct. 

Citizen devices persist emergency SOS requests locally in a Room database before any transmission. Nearby nodes forward messages using an application-level store-and-forward protocol. When any intermediate device eventually reaches internet connectivity (cellular, satellite, or emergency Wi-Fi hotspot), it uploads buffered requests to the central ResQhunT backend, where emergency coordinators triage incidents and dispatch volunteers.

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

## 2. Component Boundaries & Responsibilities

### 2.1 Android Application (`/android`)
- **UI Layer (Jetpack Compose)**: Reactive screens for Onboarding, Authentication, Citizen Home, One-Tap SOS, Status Tracking, Nearby Peers, Offline Queue, Location, and Emergency Contacts.
- **Data Layer (Room Database)**:
  - `SosEntity`: Primary emergency records created on device.
  - `RelayMessageEntity`: Inbound and outbound forwarded envelopes with deduplication indexing.
  - `PeerEntity`: Discovered peer devices with connection states and RSSI/timestamp tracking.
  - `ResourceCacheEntity`: Cached relief centers, shelter points, and emergency hotlines.
- **Networking & Mesh Layer**:
  - `NearbyConnectionsManager`: Manages advertising, discovery, connection lifecycle, and payload exchange via Google Nearby Connections (`Strategy.P2P_CLUSTER`).
  - `StoreAndForwardRelayEngine`: Validates envelopes, checks signatures and TTL, enforces hop limits, prevents routing loops, and manages queue retries.
  - `BatteryAwareRouter`: Adjusts discovery and advertising intervals based on Android `BatteryManager` levels to preserve critical battery during emergencies.
  - `ResqHuntApiClient`: Syncs pending local requests to cloud when internet is available.

### 2.2 Backend Application (`/backend`)
- **Runtime**: Node.js, Express, TypeScript.
- **ORM & Database**: Prisma ORM with PostgreSQL (Supabase ready).
- **Core Modules**:
  - `authController`: JWT token issuance, bcrypt password hashing, and role verification (CITIZEN, VOLUNTEER, COORDINATOR, ADMIN).
  - `sosController`: Idempotent batch synchronization (`POST /api/sos/sync`) handling incoming relay envelopes without duplicates.
  - `priorityEngine`: Deterministic rule-based priority calculation taking into account severity, medical urgency, affected count, and age.
  - `incidentController`: Coordinator triage, status transitions, and priority overrides.
  - `volunteerController`: Volunteer registration, skill tagging, and assignment workflows.
  - `resourceController`: Transactional inventory allocation preventing negative quantities.
  - `activityController`: Immutable audit logging for coordinator overrides and dispatches.

### 2.3 Web Command Center (`/web`)
- **Runtime**: React 18, Vite, TypeScript, Tailwind CSS, Lucide Icons, Leaflet.
- **Color Identity**:
  - Navy: `#10233F` (Primary commands, headers, sidebars)
  - Emergency Red: `#DC2626` (Critical alerts, SOS markers, urgent state)
  - Teal: `#18B6A4` (Resolved items, positive sync, active mesh indicators)
  - Background: `#F4F7FB` (Neutral workspace background)
  - White: `#FFFFFF` (Card surfaces, modals)
  - Text: `#172033` (High-contrast typography)
- **Features**:
  - Interactive Leaflet OpenStreetMap with real-time incident markers and relief shelters.
  - List-based fallback view when offline or when map tile servers are unreachable.
  - Triage modal for status transitions and priority overrides.
  - Volunteer dispatch matching emergency categories to skillsets.
  - Relief resource allocation with real-time stock bounds checking.
  - Deterministic Demo Mode banner with sample dataset reload.

---

## 3. Data Flow & State Machine

### 3.1 Emergency Lifecycle State Machine
```
[CREATED] (User presses SOS button)
   │
   ▼
[STORED_LOCALLY] (Room DB write confirmed)
   │
   ├──────────────────────────────┬──────────────────────────────┐
   │ (Internet Available)         │ (No Internet)                │ (No Peers)
   ▼                              ▼                              ▼
[SERVER_RECEIVED]          [RELAY_PENDING]                [STORED_LOCALLY]
   │                              │                         (Retry loop)
   │                              ▼
   │                       [RELAYED_TO_PEER]
   │                              │ (Peer forwards to Internet)
   └──────────────────────────────┘
                                  │
                                  ▼
                   [COORDINATOR_ACKNOWLEDGED]
                                  │
                                  ▼
                             [ASSIGNED]
                                  │
                                  ▼
                            [IN_PROGRESS]
                                  │
                                  ▼
                             [RESOLVED]
```

### 3.2 Security & Integrity Model
1. **Payload Integrity**: Every mesh message envelope contains a SHA-256 integrity hash of its payload and origin device identifier.
2. **Replay & Loop Prevention**: Deduplication table in Room with unique `messageId` and origin tracking prevents re-forwarding messages back to their source or already traversed hops.
3. **Transport Security**: Backend strictly requires HTTPS/TLS in production, and JWT bearer tokens for authenticated coordinator and volunteer operations.
4. **Least Privilege**: Sensitive citizen personal info is minimized in broadcast envelopes (only category, severity, victim count, coordinates, and short description are relayed).
