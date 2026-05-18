package com.bitecma.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.bitecma.app.ui.dashboard.DocumentosScreen
import com.bitecma.app.ui.dashboard.IngresosScreen
import com.bitecma.app.data.AppState
import com.bitecma.app.data.DataManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.loadSession(this)
        DataManager.loadCache(this)
        val startDestination = if (AppState.currentUserId != null) {
            "dashboard/${AppState.currentUserId}"
        } else {
            "login"
        }
        setContent {
            var isDarkMode by remember { mutableStateOf(false) }
            val ctx = this@MainActivity
            LaunchedEffect(AppState.authToken, AppState.forceOffline) {
                if (!AppState.authToken.isNullOrBlank() && !AppState.forceOffline) {
                    runCatching { DataManager.syncAllFromServer(ctx) }
                }
            }
            
            val navyBlue = Color(0xFF1B263B) // Azul marino relajante
            val softGray = Color(0xFFF5F5F5) // Gris suave para la vista

            val colorScheme = if (isDarkMode) {
                darkColorScheme(
                    primary = Color(0xFF415A77),
                    background = navyBlue,
                    surface = Color(0xFF0D1B2A)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF003366),
                    background = softGray,
                    surface = Color.White
                )
            }
            
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("login") { LoginScreen(navController) }
                        composable("forgot_password/{email}") { backStackEntry ->
                            val email = backStackEntry.arguments?.getString("email") ?: ""
                            ForgotPasswordScreen(navController, email)
                        }
                        composable("dashboard/{userId}") { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            DashboardScreen(navController, userId, isDarkMode, onDarkModeChange = { isDarkMode = it })
                        }
                        composable("operaciones/{userId}") { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            OperacionesScreen(navController, userId)
                        }
                        composable("especies/{userId}") { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            EspeciesScreen(navController, userId)
                        }
                        composable("botes/{userId}") { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            BotesScreen(navController, userId)
                        }
                        composable("documentos/{userId}") { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            DocumentosScreen(navController, userId)
                        }
                        composable("ingresos/{userId}") { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            IngresosScreen(navController, userId)
                        }
                        composable("admin/{userId}") { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0
                            AdminScreen(navController, userId)
                        }
                    }
                }
            }
        }
    }
}
