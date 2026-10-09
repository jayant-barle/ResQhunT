# ResQhunT Hackathon Demonstration Script

This script provides an exact 3-minute, high-impact demonstration flow for hackathon judges and evaluation panels.

## 1. Introduction (0:00 - 0:30)
- **Presenter**: *"When earthquakes or cyclones strike, cell towers and power grids are the first to collapse. Victims cannot call 911 or reach help. This is where ResQhunT steps in: 'When Networks Fail, ResQhunT Connects.' ResQhunT transforms everyday Android phones into an emergency ad-hoc mesh network without any cellular data, Wi-Fi router, or internet."*

---

## 2. Part 1: Offline SOS Creation on Citizen Phone (0:30 - 1:15)
- **Action**: Display the Citizen Android app. Put the phone in **Airplane Mode**.
- **Demonstration**:
  1. Show that Wi-Fi and Cellular are completely disabled.
  2. Tap **"ONE-TAP SOS"** on the home screen.
  3. Select Category: **MEDICAL**, Severity: **CRITICAL**, Victims: **2**.
  4. Tap **"CONFIRM EMERGENCY"**. Notice the 5-second accidental cancel safety countdown.
  5. The request is immediately committed to the local **Room SQLite database**.
  6. Point out the delivery state tracker:
     - `[X] CREATED`
     - `[X] STORED LOCALLY`
     - `[ ] RELAY PENDING (Searching for Nearby Peers...)`

---

## 3. Part 2: Peer-to-Peer Mesh Propagation (1:15 - 2:00)
- **Action**: Bring a second Android device (Relay Node B) close to Phone A.
- **Demonstration**:
  1. Device B detects Device A via Google Nearby Connections (`Strategy.P2P_CLUSTER`).
  2. The serialized SHA-256 verified envelope is transferred over BLE/Wi-Fi Direct.
  3. Device A's status updates in real-time to:
     - `[X] RELAYED TO PEER`
  4. Device B now carries the buffered emergency in its offline store-and-forward queue.
  5. Explain hop counters, deduplication to prevent broadcast storms, and battery-aware throttling.

---

## 4. Part 3: Gateway Sync & Web Command Center (2:00 - 2:45)
- **Action**: Switch to Device C (or re-enable Internet on Relay Node) and open the **Web Rescue Command Dashboard**.
- **Demonstration**:
  1. The buffered request hits `POST /api/sos/sync`.
  2. The dashboard updates live:
     - Total Incidents increments.
     - Pulsing red emergency marker appears on the **Leaflet OpenStreetMap** at the victim's exact GPS coordinates.
     - The **Deterministic Priority Engine** assigns a score of 92.5/100 based on `CRITICAL MEDICAL + 2 VICTIMS`.
  3. Log in as **Coordinator**.
  4. Click **"Acknowledge Incident"** -> Delivery status transitions to `COORDINATOR_ACKNOWLEDGED`.
  5. Dispatch an available volunteer with first-aid certification (`ASSIGNED`).
  6. Allocate 2 units of Emergency Medical Kits from Relief Resources -> Show that the inventory safely decrements and rejects any over-allocation.
  7. Show the **Activity Log** recording the complete audit trail.

---

## 5. Conclusion & Technical Highlights (2:45 - 3:00)
- **Key Takeaways**:
  - Offline-first Room persistence guarantees zero data loss on crash.
  - Multi-hop store-and-forward routing bridges miles without cell towers.
  - Full-stack coordination platform: Android (Kotlin, Jetpack Compose, Nearby Connections, Room) + Backend (Node.js, Express, TypeScript, Prisma) + Web Dashboard (React, Tailwind, Leaflet).
  - Open source, battle-tested architecture for disaster resilience.
