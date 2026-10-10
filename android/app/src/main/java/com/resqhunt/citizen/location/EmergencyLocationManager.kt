package com.resqhunt.citizen.location

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class LocationPermissionState {
    PRECISE,      // ACCESS_FINE_LOCATION granted
    APPROXIMATE,  // ACCESS_COARSE_LOCATION only granted (Android 12+)
    DENIED        // Neither granted
}

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val source: String, // FRESH_GPS, LAST_KNOWN, APPROXIMATE_COARSE, MANUAL, UNAVAILABLE
    val provider: String = "fused"
) {
    val isFresh: Boolean get() = (System.currentTimeMillis() - timestamp) <= 60_000L && source == "FRESH_GPS"
    val isApproximate: Boolean get() = source == "APPROXIMATE_COARSE" || accuracy > 100.0f
    val ageMs: Long get() = Math.max(0L, System.currentTimeMillis() - timestamp)
}

class EmergencyLocationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ResQhunT_Location"
        private const val PREFS_NAME = "resqhunt_location_cache"
        private const val KEY_LAT = "cached_latitude"
        private const val KEY_LON = "cached_longitude"
        private const val KEY_ACC = "cached_accuracy"
        private const val KEY_TIME = "cached_timestamp"
        private const val KEY_SRC = "cached_source"
        private const val KEY_PROV = "cached_provider"

        @Volatile
        private var instance: EmergencyLocationManager? = null

        fun getInstance(context: Context): EmergencyLocationManager {
            return instance ?: synchronized(this) {
                instance ?: EmergencyLocationManager(context.applicationContext).also { instance = it }
            }
        }

        fun isValidCoordinates(lat: Double?, lon: Double?): Boolean {
            if (lat == null || lon == null) return false
            // Strict rejection of Null Island (0.0, 0.0)
            if (lat == 0.0 && lon == 0.0) return false
            if (lat.isNaN() || lon.isNaN()) return false
            if (lat < -90.0 || lat > 90.0) return false
            if (lon < -180.0 || lon > 180.0) return false
            return true
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val systemLocationManager: LocationManager? by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    private val _currentFix = MutableStateFlow<LocationFix?>(null)
    val currentFix: StateFlow<LocationFix?> = _currentFix.asStateFlow()

    init {
        // Load persisted last known fix if available
        loadPersistedFix()
    }

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasLocationPermission(): Boolean = hasFineLocationPermission() || hasCoarseLocationPermission()

    fun isApproximateOnly(): Boolean = hasCoarseLocationPermission() && !hasFineLocationPermission()

    fun getPermissionState(): LocationPermissionState {
        return when {
            hasFineLocationPermission() -> LocationPermissionState.PRECISE
            hasCoarseLocationPermission() -> LocationPermissionState.APPROXIMATE
            else -> LocationPermissionState.DENIED
        }
    }

    fun isLocationEnabled(): Boolean {
        val lm = systemLocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun isGpsProviderEnabled(): Boolean {
        val lm = systemLocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun isNetworkProviderEnabled(): Boolean {
        val lm = systemLocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Attempts to acquire the freshest possible high-accuracy location fix within maxWaitMs.
     * 1. Checks cached fix.
     * 2. Checks Google FusedLocationProvider lastLocation (or fallback system LocationManager lastKnown).
     * 3. Requests a fresh high-accuracy location fix from FusedLocationProvider (with fallback to GPS_PROVIDER).
     * 4. If fresh fix fails/times out, cleanly returns the last known lock with its actual timestamp and accuracy.
     * 5. If no location is available, returns null. NEVER substitutes 0,0, city-centre, or fabricated coordinates.
     */
    suspend fun acquireCurrentOrLastKnownLocation(maxWaitMs: Long = 3000L): LocationFix? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted. Falling back to cached fix if present.")
            return@withContext getBestCachedFix()
        }

        if (!isLocationEnabled()) {
            Log.w(TAG, "Device location services disabled. Falling back to cached fix if present.")
            return@withContext getBestCachedFix()
        }

        // 1. Check FusedLocationProvider lastLocation and System lastKnown
        var candidateLastKnown: LocationFix? = null

        val fusedLast = try {
            fetchFusedLastLocation()
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching fused lastLocation: ${e.message}")
            null
        }

        val bestLast = fusedLast ?: fetchSystemLastKnownLocation()

        if (bestLast != null && isValidCoordinates(bestLast.latitude, bestLast.longitude)) {
            val ageMs = System.currentTimeMillis() - bestLast.time
            val isAccurate = bestLast.accuracy > 0 && bestLast.accuracy <= 30.0f
            if (ageMs in 0..25_000L && isAccurate && hasFineLocationPermission()) {
                // Under 25 seconds old and high accuracy: accept as fresh GPS fix immediately
                val fix = LocationFix(
                    latitude = bestLast.latitude,
                    longitude = bestLast.longitude,
                    accuracy = bestLast.accuracy,
                    timestamp = bestLast.time,
                    source = "FRESH_GPS",
                    provider = bestLast.provider ?: "fused"
                )
                saveFix(fix)
                Log.i(TAG, "Acquired fresh recent GPS fix (age=${ageMs}ms, acc=±${bestLast.accuracy}m)")
                return@withContext fix
            } else {
                // Older or coarse: cache as candidate last known with its actual timestamp
                val src = if (isApproximateOnly() || bestLast.accuracy > 100.0f) "APPROXIMATE_COARSE" else "LAST_KNOWN"
                candidateLastKnown = LocationFix(
                    latitude = bestLast.latitude,
                    longitude = bestLast.longitude,
                    accuracy = bestLast.accuracy,
                    timestamp = bestLast.time,
                    source = src,
                    provider = bestLast.provider ?: "last_known"
                )
                saveFix(candidateLastKnown)
            }
        }

        // 2. Request a fresh high-accuracy fix with timeout to never block SOS delivery
        val freshFix = withTimeoutOrNull(maxWaitMs) {
            val fusedFix = fetchFreshFusedLocation()
            if (fusedFix != null && isValidCoordinates(fusedFix.latitude, fusedFix.longitude)) {
                fusedFix
            } else {
                fetchFreshSystemLocation()
            }
        }

        if (freshFix != null && isValidCoordinates(freshFix.latitude, freshFix.longitude)) {
            val src = if (isApproximateOnly() || freshFix.accuracy > 100.0f) "APPROXIMATE_COARSE" else "FRESH_GPS"
            val fix = LocationFix(
                latitude = freshFix.latitude,
                longitude = freshFix.longitude,
                accuracy = freshFix.accuracy,
                timestamp = if (freshFix.time > 0) freshFix.time else System.currentTimeMillis(),
                source = src,
                provider = freshFix.provider ?: "fused"
            )
            saveFix(fix)
            Log.i(TAG, "Acquired fresh high-accuracy fix: (${fix.latitude}, ${fix.longitude}) ±${fix.accuracy}m [$src via ${fix.provider}]")
            return@withContext fix
        }

        // 3. Fallback: Return best candidate or cached last-known fix with genuine timestamp
        return@withContext candidateLastKnown ?: getBestCachedFix()
    }

    private suspend fun fetchFusedLastLocation(): Location? = suspendCancellableCoroutine { cont ->
        try {
            if (!hasLocationPermission()) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            fusedLocationClient.lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException fetching fused lastLocation: ${e.message}")
            cont.resume(null)
        } catch (e: Exception) {
            Log.w(TAG, "Exception fetching fused lastLocation: ${e.message}")
            cont.resume(null)
        }
    }

    private suspend fun fetchFreshFusedLocation(): Location? = suspendCancellableCoroutine { cont ->
        try {
            if (!hasLocationPermission()) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }

            val priority = if (hasFineLocationPermission()) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

            fusedLocationClient.getCurrentLocation(priority, cts.token)
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Fused getCurrentLocation failed: ${e.message}")
                    cont.resume(null)
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException in fused getCurrentLocation: ${e.message}")
            cont.resume(null)
        } catch (e: Exception) {
            Log.w(TAG, "Exception in fused getCurrentLocation: ${e.message}")
            cont.resume(null)
        }
    }

    private fun fetchSystemLastKnownLocation(): Location? {
        val lm = systemLocationManager ?: return null
        return try {
            if (hasFineLocationPermission() && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else if (hasLocationPermission() && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else {
                null
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchFreshSystemLocation(): Location? = suspendCancellableCoroutine { cont ->
        val lm = systemLocationManager
        if (lm == null || !hasLocationPermission()) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            val provider = when {
                hasFineLocationPermission() && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                hasLocationPermission() && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }

            if (provider == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(location)
                }
                override fun onStatusChanged(p: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(null)
                }
            }

            cont.invokeOnCancellation {
                try { lm.removeUpdates(listener) } catch (_: Exception) {}
            }

            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException in fallback LocationManager: ${e.message}")
            if (cont.isActive) cont.resume(null)
        } catch (e: Exception) {
            Log.w(TAG, "Exception in fallback LocationManager: ${e.message}")
            if (cont.isActive) cont.resume(null)
        }
    }

    fun getBestCachedFix(): LocationFix? {
        val current = _currentFix.value
        if (current != null && isValidCoordinates(current.latitude, current.longitude)) {
            return current
        }
        return loadPersistedFix()
    }

    private fun saveFix(fix: LocationFix) {
        _currentFix.value = fix
        prefs.edit()
            .putString(KEY_LAT, fix.latitude.toString())
            .putString(KEY_LON, fix.longitude.toString())
            .putFloat(KEY_ACC, fix.accuracy)
            .putLong(KEY_TIME, fix.timestamp)
            .putString(KEY_SRC, fix.source)
            .putString(KEY_PROV, fix.provider)
            .apply()
        Log.i(TAG, "Saved location lock: ${fix.latitude}, ${fix.longitude} (source=${fix.source}, acc=±${fix.accuracy}m, time=${fix.timestamp}, prov=${fix.provider})")
    }

    private fun loadPersistedFix(): LocationFix? {
        val latStr = prefs.getString(KEY_LAT, null) ?: return null
        val lonStr = prefs.getString(KEY_LON, null) ?: return null
        val lat = latStr.toDoubleOrNull() ?: return null
        val lon = lonStr.toDoubleOrNull() ?: return null
        val acc = prefs.getFloat(KEY_ACC, 20.0f)
        val time = prefs.getLong(KEY_TIME, 0L)
        val src = prefs.getString(KEY_SRC, "LAST_KNOWN") ?: "LAST_KNOWN"
        val prov = prefs.getString(KEY_PROV, "persisted") ?: "persisted"

        if (!isValidCoordinates(lat, lon)) return null

        val fix = LocationFix(
            latitude = lat,
            longitude = lon,
            accuracy = acc,
            timestamp = time,
            source = if (src == "FRESH_GPS") "LAST_KNOWN" else src,
            provider = prov
        )
        _currentFix.value = fix
        return fix
    }
}
