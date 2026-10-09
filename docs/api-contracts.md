# ResQhunT API Contracts & Protocol Specifications

## 1. Store-and-Forward Mesh Relay Envelope

When two Android devices connect over Google Nearby Connections, payloads are serialized JSON byte arrays formatted according to this specification.

### 1.1 Mesh Envelope JSON Schema
```json
{
  "messageId": "msg_98a7f2e1-4567-4890-a123-abcdef012345",
  "requestId": "sos_12345678-1234-5678-1234-567812345678",
  "originDeviceId": "dev_android_3a8b2c",
  "messageType": "EMERGENCY_SOS",
  "protocolVersion": 1,
  "createdAt": 1728470400000,
  "expiresAt": 1728556800000,
  "hopCount": 1,
  "maxHops": 5,
  "payload": {
    "category": "MEDICAL",
    "severity": "CRITICAL",
    "affectedCount": 3,
    "description": "Building collapse near Sector 4, 3 injured with fractures",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "locationAccuracy": 12.5,
    "locationAddress": "Connaught Place Block B",
    "emergencyContacts": [
      { "name": "John Doe", "phone": "+919876543210" }
    ],
    "medicalFlags": {
      "requiresTriage": true,
      "unconsciousVictims": false,
      "severeBleeding": true
    }
  },
  "integrity": {
    "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "algorithm": "SHA-256"
  }
}
```

### 1.2 Envelope Types
- `EMERGENCY_SOS`: Primary emergency beacon containing incident location and details.
- `DELIVERY_ACK`: Reverse hop confirmation when an internet-connected node uploads the message to the server.
- `RESOURCE_INFO`: Broadcast message informing nodes of nearest open relief centers.

---

## 2. REST API Endpoints

### 2.1 Authentication (`/api/auth`)

#### `POST /api/auth/register`
Creates a new user account.
- **Request Body**:
  ```json
  {
    "email": "coordinator@resqhunt.org",
    "password": "SecurePassword123!",
    "fullName": "Dr. Sarah Connor",
    "phone": "+919876543210",
    "role": "COORDINATOR" // CITIZEN | VOLUNTEER | COORDINATOR | ADMIN
  }
  ```
- **Response `201 Created`**:
  ```json
  {
    "token": "eyJhbGciOi...",
    "user": {
      "id": "usr_9918237",
      "email": "coordinator@resqhunt.org",
      "fullName": "Dr. Sarah Connor",
      "role": "COORDINATOR"
    }
  }
  ```

#### `POST /api/auth/login`
Authenticates a user.
- **Request Body**:
  ```json
  {
    "email": "coordinator@resqhunt.org",
    "password": "SecurePassword123!"
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "token": "eyJhbGciOi...",
    "user": {
      "id": "usr_9918237",
      "email": "coordinator@resqhunt.org",
      "fullName": "Dr. Sarah Connor",
      "role": "COORDINATOR"
    }
  }
  ```

---

### 2.2 Emergency Synchronization (`/api/sos`)

#### `POST /api/sos/sync`
**Idempotent sync endpoint** called by any Android device that has obtained an internet connection. Can receive single or batched envelopes.
- **Headers**:
  - `Content-Type: application/json`
  - `Authorization: Bearer <token>` (Optional for emergency citizen uploads, required for volunteer/coordinator tagged sync)
- **Request Body**:
  ```json
  {
    "envelopes": [
      {
        "messageId": "msg_98a7f2e1-4567-4890-a123-abcdef012345",
        "requestId": "sos_12345678-1234-5678-1234-567812345678",
        "originDeviceId": "dev_android_3a8b2c",
        "messageType": "EMERGENCY_SOS",
        "protocolVersion": 1,
        "createdAt": 1728470400000,
        "expiresAt": 1728556800000,
        "hopCount": 2,
        "maxHops": 5,
        "payload": {
          "category": "MEDICAL",
          "severity": "CRITICAL",
          "affectedCount": 3,
          "description": "Building collapse near Sector 4",
          "latitude": 28.6139,
          "longitude": 77.2090,
          "locationAccuracy": 12.5,
          "locationAddress": "Connaught Place Block B"
        },
        "integrity": {
          "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
          "algorithm": "SHA-256"
        }
      }
    ]
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "processed": 1,
    "duplicates": 0,
    "results": [
      {
        "requestId": "sos_12345678-1234-5678-1234-567812345678",
        "status": "SERVER_RECEIVED",
        "priorityScore": 92.5,
        "priorityCategory": "CRITICAL",
        "receivedAt": "2026-10-09T08:30:00.000Z"
      }
    ]
  }
  ```

---

### 2.3 Incidents & Command Triage (`/api/incidents`)

#### `GET /api/incidents`
Lists incidents with filtering and pagination.
- **Query Params**: `status`, `category`, `severity`, `page`, `limit`
- **Response `200 OK`**:
  ```json
  {
    "incidents": [
      {
        "id": "inc_abc123",
        "requestId": "sos_12345678-1234-5678-1234-567812345678",
        "category": "MEDICAL",
        "severity": "CRITICAL",
        "affectedCount": 3,
        "description": "Building collapse near Sector 4",
        "latitude": 28.6139,
        "longitude": 77.2090,
        "locationAddress": "Connaught Place Block B",
        "deliveryState": "COORDINATOR_ACKNOWLEDGED",
        "priorityScore": 92.5,
        "priorityCategory": "CRITICAL",
        "createdAt": "2026-10-09T08:30:00.000Z",
        "assignments": []
      }
    ],
    "total": 1,
    "page": 1,
    "totalPages": 1
  }
  ```

#### `PATCH /api/incidents/:id/status`
Updates incident status (Requires COORDINATOR or ADMIN).
- **Request Body**:
  ```json
  {
    "status": "COORDINATOR_ACKNOWLEDGED", // COORDINATOR_ACKNOWLEDGED | ASSIGNED | IN_PROGRESS | RESOLVED
    "note": "Triage team 2 dispatched"
  }
  ```

#### `PATCH /api/incidents/:id/priority-override`
Coordinator overrides computed priority score with justification.
- **Request Body**:
  ```json
  {
    "newScore": 98.0,
    "newCategory": "CRITICAL",
    "reason": "Direct communication confirmed gas leak hazard adjacent to building collapse"
  }
  ```

---

### 2.4 Volunteers & Assignments (`/api/volunteers`, `/api/assignments`)

#### `GET /api/volunteers`
Returns volunteer roster with skills and availability.

#### `POST /api/assignments`
Assigns a volunteer to an incident.
- **Request Body**:
  ```json
  {
    "incidentId": "inc_abc123",
    "volunteerId": "vol_xyz789",
    "notes": "Medical kit #4 equipped"
  }
  ```

#### `PATCH /api/assignments/:id/status`
Volunteer updates assignment state.
- **Request Body**:
  ```json
  {
    "status": "IN_PROGRESS", // ACCEPTED | DECLINED | IN_PROGRESS | COMPLETED
    "notes": "Arrived on scene, administering first aid"
  }
  ```

---

### 2.5 Relief Resources (`/api/resources`)

#### `GET /api/resources`
Returns inventory of food, water, medical kits, and shelters.

#### `POST /api/resources/allocate`
Transactionally allocates items to an incident, preventing negative stock.
- **Request Body**:
  ```json
  {
    "resourceId": "res_water_tank_01",
    "incidentId": "inc_abc123",
    "quantity": 50
  }
  ```
- **Response `200 OK`**:
  ```json
  {
    "success": true,
    "allocationId": "alloc_554433",
    "remainingQuantity": 450
  }
  ```
- **Response `400 Bad Request`** (When requested quantity exceeds available stock):
  ```json
  {
    "error": "INSUFFICIENT_STOCK",
    "message": "Requested 500 units, but only 450 units are currently in stock."
  }
  ```
