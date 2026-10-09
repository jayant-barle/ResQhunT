# ResQhunT Testing Plan & Verification Guide

## 1. Test Strategy Overview
ResQhunT relies on a multi-tiered verification hierarchy to guarantee mission-critical reliability:
1. **Unit Tests**: Deterministic validation of pure functions (message envelope serialization, integrity hashing, deduplication filter, priority calculation engine, battery routing thresholds).
2. **Backend Integration Tests**: REST endpoint validation, JWT authentication, RBAC policy checks, idempotent sync deduplication, and transactional inventory guarantees.
3. **Android Persistence Tests**: Room database DAOs, local entity migrations, and state machine transitions.
4. **Physical Device Testing**: Real BLE/Wi-Fi Direct testing using Google Nearby Connections API on 2 to 3 physical Android smartphones.

---

## 2. Automated Test Matrix

| Test Suite | File Path | Focus | Target Coverage |
|:---|:---|:---|:---|
| **Priority Engine Test** | `backend/src/tests/priorityEngine.test.ts` | Urgency formula, age weighting, severity overrides | 100% |
| **Auth & RBAC Test** | `backend/src/tests/auth.test.ts` | Passwords, JWT tokens, Citizen vs Coordinator roles | 100% |
| **Idempotent Sync Test** | `backend/src/tests/sosSync.test.ts` | Duplicate requestId suppression, status transitions | 100% |
| **Resource Allocation Test** | `backend/src/tests/resourceAllocation.test.ts` | Transaction safety, negative stock prevention | 100% |
| **Mesh Envelope Unit Test** | `android/.../MeshProtocolEnvelopeTest.kt` | JSON parsing, checksum validation, TTL expiry, hop logic | 100% |
| **Mesh Deduplication Test** | `android/.../StoreAndForwardDeduplicationTest.kt` | Unique message ID filtering, loop suppression | 100% |
| **Android Room DB Test** | `android/.../RoomPersistenceLogicTest.kt` | Local SOS persistence, state machine update | 100% |

---

## 3. Physical Multi-Hop Verification Protocol

### Test Equipment Requirements
- **Device A (Victim)**: Physical Android device (Android 10+, Google Play services installed). Airplane mode active, Bluetooth ENABLED, Wi-Fi ENABLED (disconnected from router).
- **Device B (Relay)**: Physical Android device. Airplane mode active, Bluetooth ENABLED, Wi-Fi ENABLED.
- **Device C (Gateway)**: Physical Android device with active Internet connection (Cellular 4G/5G or Wi-Fi).

### Step-by-Step Multi-Hop Test Scenario

#### Step 1: Initial State Setup
1. Launch ResQhunT Citizen on Devices A, B, and C.
2. Confirm Device A and Device B have NO internet access.
3. Confirm Device C is connected to the Internet and has network access to the ResQhunT backend server.

#### Step 2: Emergency Generation on Device A
1. On Device A, select emergency category `MEDICAL`.
2. Enter affected count `2`, description: `Test Victim Bleeding`.
3. Press `TRIGGER SOS`. Observe the 5-second cancel countdown.
4. Allow countdown to complete.
5. **Expected Result on Device A**:
   - Status changes immediately to `STORED_LOCALLY`.
   - SOS card appears with orange indicator: `RELAY_PENDING (Searching for nearby peers...)`.

#### Step 3: Peer Hop 1 (Device A -> Device B)
1. Bring Device B within 10 meters of Device A.
2. In ResQhunT on Device B, activate "Active Relay Node" mode.
3. Nearby Connections establishes a cluster connection.
4. **Expected Result on Device B**:
   - Device B receives the serialized JSON envelope.
   - Message ID is logged in Device B's Room database.
   - Hop count is incremented from `1` to `2`.
   - Status on Device A updates to `RELAYED_TO_PEER`.

#### Step 4: Peer Hop 2 (Device B -> Device C)
1. Device B moves near Device C.
2. Device B's Store-and-Forward queue identifies Device C as a new eligible peer.
3. Message envelope is forwarded to Device C.
4. **Expected Result on Device C**:
   - Device C receives envelope with hop count `2`.
   - Device C detects active Internet connectivity.
   - Device C automatically triggers `POST /api/sos/sync` to the backend.

#### Step 5: Backend & Web Dashboard Verification
1. Open the Web Rescue Command Dashboard on a desktop browser.
2. **Expected Result**:
   - Incident appears on the Overview and Incidents table within 1 second.
   - Priority score is calculated dynamically based on `MEDICAL` + `CRITICAL`.
   - Coordinates appear on the Leaflet map with a pulsing red marker.
   - Status is marked as `SERVER_RECEIVED`.
   - Delivery acknowledgement envelope is generated.

---

## 4. Failure Mode Scenarios

| Scenario | Injected Condition | Expected Behavior |
|:---|:---|:---|
| **GPS Unavailable** | Device location services toggled OFF | Request persists with null coordinates; user prompted for landmark address |
| **Bluetooth Disabled** | Bluetooth toggled OFF during SOS | Persistent warning banner displayed; Room request remains safely queued |
| **No Peers Available** | Device isolated for 1 hour | Request remains in Room with `STORED_LOCALLY`; exponential backoff retry active |
| **Network Timeout on Sync** | Gateway device drops connection mid-upload | Request retained in outbound sync queue; retried upon network reconnection |
| **Duplicate Message Broadcast**| Malicious or looping node resends message | Discarded immediately via unique messageId index; no duplicate database rows |
