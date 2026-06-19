package com.bitecma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.*
import com.bitecma.app.ui.login.LoginScreen
import com.bitecma.app.ui.dashboard.DashboardScreen
import com.bitecma.app.ui.login.ForgotPasswordScreen
import com.bitecma.app.ui.dashboard.OperacionesScreen
import com.bitecma.app.ui.dashboard.EspeciesScreen
import com.bitecma.app.data.AppState
import com.bitecma.app.data.DataManager
import com.bitecma.app.sync.SyncScheduler
import com.bitecma.app.ui.BitecmaTheme
import com.bitecma.app.utils.NetworkMonitor

class MainActivity : ComponentActivity() {
    companion object {
        private const val PREFS_UI = "bitecma_ui"
        private const val KEY_DARK_MODE = "dark_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.loadSession(this)
        val startDestination = if (AppState.hasAppAccess()) {
            AppRoutes.dashboard(AppState.dashboardUserId())
        } else {
            AppRoutes.LOGIN
        }
        setContent {
            val darkModeInicial = remember {
                getSharedPreferences(PREFS_UI, MODE_PRIVATE).getBoolean(KEY_DARK_MODE, false)
            }
            var isDarkMode by remember { mutableStateOf(darkModeInicial) }
            var cacheReady by remember { mutableStateOf(false) }
            val ctx = this@MainActivity
            val darkModeSistema = isSystemInDarkTheme()

            LaunchedEffect(darkModeSistema) {
                val prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE)
                if (!prefs.contains(KEY_DARK_MODE)) {
                    isDarkMode = darkModeSistema
                }
            }

            LaunchedEffect(Unit) {
                if (AppState.hasAuthenticatedSession()) {
                    DataManager.loadCache(ctx)
                } else {
                    DataManager.clearLocalSessionData(ctx)
                }
                cacheReady = true
            }

            val hasNetwork by NetworkMonitor.observe(ctx).collectAsState(
                initial = NetworkMonitor.isConnected(ctx),
            )

            LaunchedEffect(hasNetwork, AppState.authToken, AppState.forceOffline) {
                AppState.hasNetwork = hasNetwork
                if (!hasNetwork) {
                    AppState.isOnline = false
                    DataManager.reconcileBackgroundSync(ctx)
                    return@LaunchedEffect
                }
                DataManager.reconcileBackgroundSync(ctx)
                if (AppState.hasAuthenticatedSession() && !AppState.forceOffline && (!AppState.authToken.isNullOrBlank() || DataManager.hasPendingSyncWork())) {
                    SyncScheduler.scheduleImmediate(ctx)
                }
            }
            
            BitecmaTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!cacheReady) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(42.dp))
                        }
                        return@Surface
                    }
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = startDestination) {
                        composable(AppRoutes.LOGIN) { LoginScreen(navController) }
                        composable(AppRoutes.FORGOT_PASSWORD_PATTERN) { backStackEntry ->
                            val email = AppRoutes.decodeForgotPasswordEmail(backStackEntry.arguments?.getString("email"))
                            ForgotPasswordScreen(navController, email)
                        }
                        composable(AppRoutes.DASHBOARD_PATTERN) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            DashboardScreen(
                                navController,
                                userId,
                                isDarkMode,
                                onDarkModeChange = { darkModeActivo ->
                                    isDarkMode = darkModeActivo
                                    getSharedPreferences(PREFS_UI, MODE_PRIVATE)
                                        .edit()
                                        .putBoolean(KEY_DARK_MODE, darkModeActivo)
                                        .apply()
                                },
                            )
                        }
                        composable(AppRoutes.OPERACIONES_PATTERN) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            OperacionesScreen(navController, userId)
                        }
                        composable(AppRoutes.ESPECIES_PATTERN) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            EspeciesScreen(navController, userId)
                        }
                    }
                }
            }
        }
    }
}
