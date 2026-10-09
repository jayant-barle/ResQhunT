package com.resqhunt.citizen.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object OneTapSos : Screen("one_tap_sos")
    object SosDetails : Screen("sos_details/{requestId}") {
        fun createRoute(requestId: String) = "sos_details/$requestId"
    }
    object MyRequests : Screen("my_requests")
    object NearbyDevices : Screen("nearby_devices")
    object OfflineQueue : Screen("offline_queue")
    object LocationSettings : Screen("location_settings")
    object EmergencyContacts : Screen("emergency_contacts")
    object ProfileSettings : Screen("profile_settings")
}
