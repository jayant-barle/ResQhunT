package com.resqhunt.citizen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.resqhunt.citizen.data.local.AppDatabase
import com.resqhunt.citizen.mesh.NearbyConnectionsManager
import com.resqhunt.citizen.mesh.StoreAndForwardRelayEngine
import com.resqhunt.citizen.ui.screens.auth.LoginScreen
import com.resqhunt.citizen.ui.screens.auth.RegisterScreen
import com.resqhunt.citizen.ui.screens.home.CitizenHomeScreen
import com.resqhunt.citizen.ui.screens.location.LocationSettingsScreen
import com.resqhunt.citizen.ui.screens.mesh.NearbyDeviceStatusScreen
import com.resqhunt.citizen.ui.screens.mesh.OfflineQueueScreen
import com.resqhunt.citizen.ui.screens.onboarding.OnboardingScreen
import com.resqhunt.citizen.ui.screens.settings.EmergencyContactsScreen
import com.resqhunt.citizen.ui.screens.settings.ProfileSettingsScreen
import com.resqhunt.citizen.ui.screens.sos.MyRequestsScreen
import com.resqhunt.citizen.ui.screens.sos.OneTapSosScreen
import com.resqhunt.citizen.ui.screens.sos.SosDetailsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    database: AppDatabase,
    nearbyManager: NearbyConnectionsManager,
    relayEngine: StoreAndForwardRelayEngine
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onGetStarted = { navController.navigate(Screen.Home.route) }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Screen.Home.route) },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = { navController.navigate(Screen.Home.route) },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            CitizenHomeScreen(
                database = database,
                onNavigateToOneTapSos = { navController.navigate(Screen.OneTapSos.route) },
                onNavigateToDetails = { id -> navController.navigate(Screen.SosDetails.createRoute(id)) },
                onNavigateToMyRequests = { navController.navigate(Screen.MyRequests.route) },
                onNavigateToNearbyDevices = { navController.navigate(Screen.NearbyDevices.route) },
                onNavigateToOfflineQueue = { navController.navigate(Screen.OfflineQueue.route) },
                onNavigateToLocation = { navController.navigate(Screen.LocationSettings.route) },
                onNavigateToContacts = { navController.navigate(Screen.EmergencyContacts.route) },
                onNavigateToSettings = { navController.navigate(Screen.ProfileSettings.route) }
            )
        }

        composable(Screen.OneTapSos.route) {
            OneTapSosScreen(
                database = database,
                relayEngine = relayEngine,
                onSosTriggered = { reqId ->
                    navController.navigate(Screen.SosDetails.createRoute(reqId)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SosDetails.route,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType })
        ) { backStackEntry ->
            val requestId = backStackEntry.arguments?.getString("requestId") ?: ""
            SosDetailsScreen(
                requestId = requestId,
                database = database,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.MyRequests.route) {
            MyRequestsScreen(
                database = database,
                onNavigateToDetails = { id -> navController.navigate(Screen.SosDetails.createRoute(id)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.NearbyDevices.route) {
            NearbyDeviceStatusScreen(
                database = database,
                nearbyManager = nearbyManager,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.OfflineQueue.route) {
            OfflineQueueScreen(
                database = database,
                relayEngine = relayEngine,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LocationSettings.route) {
            LocationSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.EmergencyContacts.route) {
            EmergencyContactsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ProfileSettings.route) {
            ProfileSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
