# ResQhunT Cross-Brand Device Compatibility & Emergency Activation Matrix
**Android 10 (API 29) – Android 15 (API 35)**

---

## 1. Executive Engineering Summary & Supported Emergency Trigger Architecture

### Volume Up + Volume Down SOS Shortcut (Foreground Only)
1. **Foreground-Only Key Event Contract:**
   - Detects `KeyEvent.KEYCODE_VOLUME_UP` and `KeyEvent.KEYCODE_VOLUME_DOWN` using standard Android Activity `dispatchKeyEvent()` handling.
   - Operates strictly while ResQhunT is open, visible, and the active focused window (`Lifecycle.State.RESUMED`).
   - Does not intercept keys when the app is in the background, closed, or when the screen is locked.
2. **Simultaneous Key Press & 3-Second Hold:**
   - Triggers SOS **ONLY** when both Volume Up and Volume Down keys are pressed simultaneously and held for 3 continuous seconds.
   - An active 3-second animated circular countdown dialog is displayed on screen (`VolumeSosCountdownDialog`).
   - Releasing either key before 3.0 seconds immediately cancels the countdown without firing.
3. **Preservation of Normal Volume Adjustment:**
   - Pressing Volume Up alone or Volume Down alone returns unhandled (`NotHandled`), allowing the Android operating system to adjust system audio volume normally.
4. **Cooldown & Duplicate Prevention:**
   - Once activated, holding the keys continuously will not re-trigger SOS.
   - Enforces a 10-second cooldown period before subsequent shortcuts can be initiated.
5. **No Accessibility Abuse or Hidden APIs:**
   - Completely adheres to AOSP platform guidelines without Accessibility Services, private APIs, or power button interception.
6. **Obsolete Power-Button Triggers Removed:**
   - Physical power-button interception has been completely removed because Android AOSP reserves `KEYCODE_POWER` for `PhoneWindowManager` (Power Menu / Assistant) and screen toggles do not reflect button hold timing.

---

## 2. Cross-Brand Trigger Compatibility Matrix

| Trigger Mechanism | Foreground State | Background State | Process Killed State | Force-Stopped State | Cross-Brand OEM Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Volume Up + Down (3s Hold)** | ✅ **Active & Guaranteed** (3s countdown + immediate mesh broadcast) | 🛑 Disabled by Design (No background key interception) | 🛑 Disabled by Design | 🛑 Blocked by Android OS | 100% standard AOSP key event delivery across all OEMs (Samsung, Xiaomi, OPPO, Vivo, Motorola, Pixel). |
| **On-Screen SOS Button** | ✅ **Active & Guaranteed** (Instant Room persistence + mesh beacon) | N/A (App not visible) | N/A | N/A | Fully supported across all Android 10–15 devices. |
| **Lock-Screen Ongoing Notification (`🚨 TRIGGER SOS NOW`)** | ✅ Active | ✅ Active (`VISIBILITY_PUBLIC`) | ⚠️ Restored upon `START_STICKY` revival | 🛑 Blocked by Android OS (`FLAG_EXCLUDE_STOPPED_PACKAGES`) | Standard Android notification action supported on all brands. |
| **Quick Settings SOS Tile (`ResQhunTTileService`)** | ✅ Active | ✅ Active (Swipe down shade) | ✅ Active (OS launches process directly) | 🛑 Blocked by Android OS | Android 7.0–15 public Quick Settings API supported on all brands. |

---

## 3. Brand-Specific Configuration & Battery Optimization

### 1. Samsung (OneUI)
- **Problem:** Samsung "Device Care" moves background services into *Sleeping apps* after inactivity.
- **Remedy:** Set `Settings > Apps > ResQhunT > Battery` to **Unrestricted**; ensure app is not in *Sleeping apps*.

### 2. Xiaomi / Redmi / POCO (MIUI / HyperOS)
- **Problem:** MIUI terminates background processes on RAM clean unless *Autostart* is granted.
- **Remedy:** Toggle **Autostart** ON in `Manage apps > ResQhunT`; select *No restrictions* under Battery saver.

### 3. OPPO / Realme / OnePlus (ColorOS / OxygenOS / Realme UI)
- **Problem:** ColorOS aggressive battery management freezes background processes when screen turns off.
- **Remedy:** Enable **Allow background activity** and **Allow auto-launch** under `Battery usage`.

### 4. Vivo / iQOO (Funtouch OS / OriginOS)
- **Problem:** Funtouch OS aggressively throttles network sockets in standby.
- **Remedy:** Select **High background power consumption** in Battery settings; enable **Autostart**.

### 5. Motorola & Google Pixel (Stock Android)
- **Problem:** Standard Android Doze mode enters deep sleep during extended stationary idle.
- **Remedy:** Select **Unrestricted** under App battery usage; add the Quick Settings tile to the top tray.

---

## 4. Lifecycle Test States & System Termination Ground Truth

- **`BACKGROUND` State:** Process alive; `SosRelayForegroundService` active with persistent notification. Lock-screen notification action and Quick Settings tile work 100%. Volume shortcut is inactive by design.
- **`PROCESS_KILLED` State:** Process evicted by LMK. Quick Settings Tile binds directly even when the process is dead; `START_STICKY` restores foreground beacon; `BootReceiver` restores service upon reboot.
- **`FORCE_STOPPED` State:** User tapped Force Stop in Settings. Android flags package with `FLAG_EXCLUDE_STOPPED_PACKAGES`. All background execution, alarms, and receivers are halted until user manually opens app launcher.
