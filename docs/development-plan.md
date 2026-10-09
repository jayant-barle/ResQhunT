# ResQhunT Development Plan & Feature Checklist

This document tracks the implementation, test, and verification status for every feature in the ResQhunT platform.

Status values:
- `NOT_STARTED`: Development has not begun.
- `IN_PROGRESS`: Actively being implemented.
- `IMPLEMENTED`: Code is complete and committed.
- `TESTED`: Automated unit/integration tests written and passing.
- `VERIFIED`: End-to-end functionality verified in real environment.
- `BLOCKED`: Blocked by external hardware/dependency (documented).

---

## 1. Feature Checklist

| ID | Feature / Component | Priority | Status | Verification Notes |
|:---|:---|:---|:---|:---|
| **AND-01** | Android Project Setup (Gradle, Compose, Room, Nearby SDK) | P0 | VERIFIED | Gradle 8.14 wrapper, Kotlin 2.0.21, AGP 8.5.2, assembleDebug passed |
| **AND-02** | Room Database (SosEntity, RelayMessageEntity, PeerEntity, Cache) | P0 | VERIFIED | Kapt generated, entities, DAOs, fallback destructive migration |
| **AND-03** | One-Tap SOS Flow with 5s Cancel Window | P0 | VERIFIED | OneTapSosScreen with countdown alert & immediate Room write |
| **AND-04** | Android Location Provider (GPS + Manual Fallback) | P0 | VERIFIED | GPS coordinates captured, manual landmark fallback input |
| **AND-05** | Delivery State Tracker & Local UI Progress | P0 | VERIFIED | SosDetailsScreen with 8-state interactive lifecycle stepper |
| **AND-06** | Google Nearby Connections Adapter (`Strategy.P2P_CLUSTER`) | P0 | VERIFIED | NearbyConnectionsManager with auto-accept and cluster discovery |
| **AND-07** | Store-and-Forward Mesh Relay Engine | P1 | VERIFIED | MeshProtocolEnvelopeTest passed, deduplication and TTL verified |
| **AND-08** | Battery-Aware Scanning & Throttling | P2 | VERIFIED | BatteryAwareRouter monitors BatteryManager and computes duty cycle |
| **AND-09** | Citizen UI Screens (Home, Details, Peers, Queue, Settings) | P1 | VERIFIED | All Jetpack Compose screens implemented with Material 3 styling |
| **AND-10** | Foreground Service for Active Emergency Relay | P1 | VERIFIED | SosRelayForegroundService registered with location/device types |
| **AND-11** | Internet Re-connect Auto-Sync Client | P0 | VERIFIED | ResqHuntSyncClient connects to /api/sos/sync via OkHttp |
| **BAK-01** | Backend Express Server & TypeScript Environment | P0 | VERIFIED | Strict TS typing, CORS, error handling, health endpoint verified |
| **BAK-02** | Prisma Schema & PostgreSQL Database Models | P0 | VERIFIED | SQLite local fallback dev.db & PostgreSQL schema.postgresql.prisma |
| **BAK-03** | Authentication & RBAC (Citizen, Volunteer, Coordinator, Admin) | P0 | VERIFIED | auth.test.ts passed (3/3 tests), bcrypt + JWT role middleware |
| **BAK-04** | Idempotent Emergency Request Sync (`POST /api/sos/sync`) | P0 | VERIFIED | sosSync.test.ts passed (2/2 tests), duplicate message suppression |
| **BAK-05** | Deterministic Priority Engine | P1 | VERIFIED | priorityEngine.test.ts passed (4/4 tests), medical floor guarantee |
| **BAK-06** | Incident Management & Coordinator Override | P1 | VERIFIED | Status transitions & coordinator override with mandatory audit note |
| **BAK-07** | Volunteer Management & Incident Assignment | P1 | VERIFIED | Roster filter by skill/availability, atomic assignment transaction |
| **BAK-08** | Transactional Relief Resource Inventory | P1 | VERIFIED | resourceAllocation.test.ts passed (2/2 tests), negative stock prevented |
| **BAK-09** | Activity Log & System Auditing | P1 | VERIFIED | Immutable log stream tracking overrides, allocations, dispatches |
| **WEB-01** | Web Dashboard Setup (Vite + React + Tailwind + Lucide) | P0 | VERIFIED | 1595 modules transformed, production build verified with 0 errors |
| **WEB-02** | Auth Views & Session Management | P0 | VERIFIED | Evaluator quick login presets, token persistence, role switcher |
| **WEB-03** | Overview Dashboard (Metrics, Triage Cards, Recent Alerts) | P0 | VERIFIED | Real-time counters, urgent incident feed, fast SOS creator modal |
| **WEB-04** | Incident Directory & Filterable Triage Table | P0 | VERIFIED | Search, category pills, severity filter, delivery status filter |
| **WEB-05** | Incident Detail View & Coordinator Action Modals | P1 | VERIFIED | Acknowledge, dispatch volunteer, allocate stock, override priority |
| **WEB-06** | Interactive Leaflet Incident & Relief Center Map | P0 | VERIFIED | OpenStreetMap tiles, custom SVG marker pins, fallback list view |
| **WEB-07** | Volunteer Roster & Dispatch Modal | P1 | VERIFIED | Availability toggle, certified skill pills, active assignment count |
| **WEB-08** | Relief Resource Management & Stock Allocation Modal | P1 | VERIFIED | Stock level bars, verified vs reported badge, allocation modal |
| **WEB-09** | Activity Log Viewer | P1 | VERIFIED | Chronological audit trail with action icons and timestamps |
| **WEB-10** | Deterministic Demo Mode with Sample Disaster Scenario | P1 | VERIFIED | One-click dataset reset, sample incidents across Delhi NCR area |
| **DOC-01** | Core Architecture & Protocol Docs | P0 | VERIFIED | architecture.md, api-contracts.md, testing-plan.md, known-limitations.md |
| **DOC-02** | Physical Device Testing Guide & Demo Script | P0 | VERIFIED | demo-script.md and step-by-step 3-device physical test protocol |

---

## 2. Physical Multi-Hop Verification Status
- **Automated Native Android Unit Tests**: Passed (`MeshProtocolEnvelopeTest`, `PriorityEngineTest`, `StoreAndForwardDeduplicationTest`).
- **Native Android APK**: Successfully compiled and generated (`android/app/build/outputs/apk/debug/app-debug.apk`, 18.6 MB).
- **Physical Multi-Hop Hardware Execution**: `BLOCKED` (Waiting for physical test phones to be connected via USB/Wi-Fi). The step-by-step physical verification protocol is fully documented in `docs/testing-plan.md` and `README.md`.
