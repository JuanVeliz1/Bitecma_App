package com.bitecma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import com.bitecma.app.ui.admin.AdminScreen
import com.bitecma.app.ui.login.ForgotPasswordScreen
import com.bitecma.app.ui.dashboard.OperacionesScreen
import com.bitecma.app.ui.dashboard.BotesScreen
import com.bitecma.app.ui.dashboard.EspeciesScreen
import com.bitecma.app.data.AppState
import com.bitecma.app.data.DataManager
import com.bitecma.app.sync.SyncScheduler
import com.bitecma.app.ui.BitecmaTheme
import com.bitecma.app.utils.NetworkMonitor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.loadSession(this)
        val startDestination = if (AppState.hasAppAccess()) {
            AppRoutes.dashboard(AppState.dashboardUserId())
        } else {
            AppRoutes.LOGIN
        }
        setContent {
            var isDarkMode by remember { mutableStateOf(false) }
            var cacheReady by remember { mutableStateOf(false) }
            val ctx = this@MainActivity

            LaunchedEffect(Unit) {
                DataManager.loadCache(ctx)
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
                            DashboardScreen(navController, userId, isDarkMode, onDarkModeChange = { isDarkMode = it })
                        }
                        composable(AppRoutes.OPERACIONES_PATTERN) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            OperacionesScreen(navController, userId)
                        }
                        composable(AppRoutes.ESPECIES_PATTERN) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            EspeciesScreen(navController, userId)
                        }
                        composable(AppRoutes.BOTES_PATTERN) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            BotesScreen(navController, userId)
                        }
                        composable(AppRoutes.ADMIN_PATTERN) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            AdminScreen(navController, userId)
                        }
                    }
                }
            }
        }
    }
}
