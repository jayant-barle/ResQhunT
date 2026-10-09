package com.resqhunt.citizen.location

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
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

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val source: String // FRESH_GPS, LAST_KNOWN, MANUAL
)

class EmergencyLocationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ResQhunT_Location"
        private const val PREFS_NAME = "resqhunt_location_cache"
        private const val KEY_LAT = "cached_latitude"
        private const val KEY_LON = "cached_longitude"
        private const val KEY_ACC = "cached_accuracy"
        private const val KEY_TIME = "cached_timestamp"
        private const val KEY_SRC = "cached_source"

        @Volatile
        private var instance: EmergencyLocationManager? = null

        fun getInstance(context: Context): EmergencyLocationManager {
            return instance ?: synchronized(this) {
                instance ?: EmergencyLocationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val _currentFix = MutableStateFlow<LocationFix?>(null)
    val currentFix: StateFlow<LocationFix?> = _currentFix.asStateFlow()

    init {
        // Load persisted last known fix if available
        loadPersistedFix()
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Attempts to acquire the freshest possible location fix within maxWaitMs.
     * 1. Checks cached fix.
     * 2. Checks FusedLocationProvider lastLocation.
     * 3. Requests a fresh high-accuracy location fix.
     * 4. If fresh fix fails/times out, cleanly returns the last known lock with its actual timestamp.
     * 5. If no location is available, returns null. NEVER substitutes 0,0.
     */
    suspend fun acquireCurrentOrLastKnownLocation(maxWaitMs: Long = 2500L): LocationFix? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted. Falling back to cached fix if present.")
            return@withContext getBestCachedFix()
        }

        if (!isLocationEnabled()) {
            Log.w(TAG, "Device location services disabled. Falling back to cached fix if present.")
            return@withContext getBestCachedFix()
        }

        // 1. Check FusedLocationProvider lastLocation first
        val fusedLast = try {
            fetchFusedLastLocation()
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching fused lastLocation: ${e.message}")
            null
        }

        if (fusedLast != null && isValidCoordinates(fusedLast.latitude, fusedLast.longitude)) {
            val ageMs = System.currentTimeMillis() - fusedLast.time
            if (ageMs in 0..45_000L) { // Under 45 seconds old: accept as fresh GPS fix
                val fix = LocationFix(
                    latitude = fusedLast.latitude,
                    longitude = fusedLast.longitude,
                    accuracy = fusedLast.accuracy,
                    timestamp = fusedLast.time,
                    source = "FRESH_GPS"
                )
                saveFix(fix)
                return@withContext fix
            } else {
                // Older than 45s: cache as last known, but still attempt a fresh fix below
                val lastKnown = LocationFix(
                    latitude = fusedLast.latitude,
                    longitude = fusedLast.longitude,
                    accuracy = fusedLast.accuracy,
                    timestamp = fusedLast.time,
                    source = "LAST_KNOWN"
                )
                saveFix(lastKnown)
            }
        }

        // 2. Request a fresh high-accuracy fix with timeout to never block SOS delivery
        val freshFix = withTimeoutOrNull(maxWaitMs) {
            fetchFreshCurrentLocation()
        }

        if (freshFix != null && isValidCoordinates(freshFix.latitude, freshFix.longitude)) {
            val fix = LocationFix(
                latitude = freshFix.latitude,
                longitude = freshFix.longitude,
                accuracy = freshFix.accuracy,
                timestamp = if (freshFix.time > 0) freshFix.time else System.currentTimeMillis(),
                source = "FRESH_GPS"
            )
            saveFix(fix)
            return@withContext fix
        }

        // 3. Fallback: Return best cached last-known fix with genuine timestamp
        return@withContext getBestCachedFix()
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
            cont.resume(null)
        } catch (e: Exception) {
            cont.resume(null)
        }
    }

    private suspend fun fetchFreshCurrentLocation(): Location? = suspendCancellableCoroutine { cont ->
        try {
            if (!hasLocationPermission()) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }

            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        } catch (e: SecurityException) {
            cont.resume(null)
        } catch (e: Exception) {
            cont.resume(null)
        }
    }

    fun getBestCachedFix(): LocationFix? {
        val current = _currentFix.value
        if (current != null && isValidCoordinates(current.latitude, current.longitude)) {
            return current
        }
        return loadPersistedFix()
    }

    private fun isValidCoordinates(lat: Double, lon: Double): Boolean {
        // Strict validation: Reject null island (0.0, 0.0) and out-of-range coordinates
        if (lat == 0.0 && lon == 0.0) return false
        if (lat < -90.0 || lat > 90.0) return false
        if (lon < -180.0 || lon > 180.0) return false
        return true
    }

    private fun saveFix(fix: LocationFix) {
        _currentFix.value = fix
        prefs.edit()
            .putString(KEY_LAT, fix.latitude.toString())
            .putString(KEY_LON, fix.longitude.toString())
            .putFloat(KEY_ACC, fix.accuracy)
            .putLong(KEY_TIME, fix.timestamp)
            .putString(KEY_SRC, fix.source)
            .apply()
        Log.i(TAG, "Saved location lock: ${fix.latitude}, ${fix.longitude} (source=${fix.source}, acc=±${fix.accuracy}m, time=${fix.timestamp})")
    }

    private fun loadPersistedFix(): LocationFix? {
        val latStr = prefs.getString(KEY_LAT, null) ?: return null
        val lonStr = prefs.getString(KEY_LON, null) ?: return null
        val lat = latStr.toDoubleOrNull() ?: return null
        val lon = lonStr.toDoubleOrNull() ?: return null
        val acc = prefs.getFloat(KEY_ACC, 20.0f)
        val time = prefs.getLong(KEY_TIME, 0L)
        val src = prefs.getString(KEY_SRC, "LAST_KNOWN") ?: "LAST_KNOWN"

        if (!isValidCoordinates(lat, lon)) return null

        val fix = LocationFix(
            latitude = lat,
            longitude = lon,
            accuracy = acc,
            timestamp = time,
            source = if (src == "FRESH_GPS") "LAST_KNOWN" else src
        )
        _currentFix.value = fix
        return fix
    }
}
