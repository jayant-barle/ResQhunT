# ResQhunT Known Limitations & Engineering Constraints

In accordance with engineering integrity and realistic disaster-relief operational conditions, the following known platform and environmental constraints are documented.

## 1. Google Nearby Connections Constraints
1. **Google Play Services Dependency**: Nearby Connections requires Google Play services on Android. Devices lacking GMS (such as certain AOSP ROMs or de-googled forks) cannot participate in the Nearby cluster.
2. **Bandwidth & Payload Size**: Nearby Connections payload transfers over BLE/Wi-Fi Direct are optimized for small messages (< 100 KB). Large high-resolution images or video feeds during disasters are not supported over multi-hop mesh; only metadata, coordinates, and short text descriptions are permitted.
3. **Connection Topology Limits**: While `Strategy.P2P_CLUSTER` allows multiple simultaneous connections, Android hardware radio chipsets typically limit simultaneous active BLE/Wi-Fi Direct connections to between 4 and 8 peers.
4. **Range Limitations**: BLE range is nominally 10 to 30 meters line-of-sight in open air, degrading rapidly inside concrete debris or submerged urban environments.

---

## 2. Android OS & Background Execution Constraints
1. **Power Manager & Doze Mode**: On Android 12 through Android 15, continuous background scanning is aggressively throttled by the OS unless an explicit user-visible foreground service (`FOREGROUND_SERVICE_CONNECTED_DEVICE` / `FOREGROUND_SERVICE_LOCATION`) is running with an ongoing notification.
2. **Hardware Power Button Tri-Press**: Detecting power button multi-press gestures requires OEM system-level firmware or accessibility services with broad system permissions that are unsuitable and restricted on modern Google Play store apps. ResQhunT instead implements an immediate One-Tap SOS widget and floating emergency action button with a 5-second accidental cancel countdown.
3. **Bluetooth & Location Toggle Permissions**: Modern Android security models prevent any third-party app from silently or programmatically enabling Bluetooth or GPS toggles without explicit user confirmation dialogs (`BluetoothAdapter.ACTION_REQUEST_ENABLE`).

---

## 3. Web & Network Constraints
1. **Offline Map Tiles**: In the web dashboard, OpenStreetMap tiles require internet access to render detailed satellite imagery or street maps. When the dashboard is operated in an offline command tent, ResQhunT provides a fallback tabular list view and cached vector pins with coordinate bearings.
2. **Store-and-Forward Latency**: Multi-hop store-and-forward mesh propagation speed is bounded by physical human mobility (physical carriers walking between disconnected clusters). Delivery times vary from seconds (when nodes are adjacent) to minutes or hours (delay-tolerant physical couriers).
3. **Database Concurrency**: The SQLite local Room database is single-writer. Inbound concurrent mesh payloads are handled through sequential Room transactions managed by Kotlin Coroutine mutexes to eliminate database locking contention.
