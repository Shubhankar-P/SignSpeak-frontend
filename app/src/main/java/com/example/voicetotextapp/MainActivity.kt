package com.example.voicetotextapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.voicetotextapp.ui.screens.ChatScreen
import com.example.voicetotextapp.ui.screens.MainScreen
import com.example.voicetotextapp.ui.screens.RoomOptionsScreen
import com.example.voicetotextapp.ui.screens.Sign2TextScreen
import com.example.voicetotextapp.ui.theme.VoiceToTextAppTheme

/**
 * Single-Activity entry point.
 *
 * All navigation that previously used startActivity() / Intent extras is now
 * handled by the NavController, with route arguments taking the place of
 * Intent.putExtra() calls.
 *
 * Route map:
 *   "main"                             → MainScreen
 *   "roomOptions/{roomCode}"           → RoomOptionsScreen
 *   "chat/{roomCode}/{role}"           → ChatScreen
 *   "sign2text/{roomCode}"             → Sign2TextScreen
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoiceToTextAppTheme {
                SignSpeakNavGraph()
            }
        }
    }
}

@Composable
fun SignSpeakNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {

        // ── Main / Home ────────────────────────────────────────────────────
        composable("main") {
            MainScreen(
                onRoomCreated = { roomCode ->
                    navController.navigate("roomOptions/$roomCode")
                },
                onJoinAsReceiver = { roomCode ->
                    navController.navigate("chat/$roomCode/receiver")
                },
                onJoinAsSign = { roomCode ->
                    navController.navigate("sign2text/$roomCode")
                }
            )
        }

        // ── Room Options ───────────────────────────────────────────────────
        composable(
            route = "roomOptions/{roomCode}",
            arguments = listOf(navArgument("roomCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: ""
            RoomOptionsScreen(
                roomCode = roomCode,
                onNavigateBack = { navController.popBackStack() },
                onSign2Text = { code -> navController.navigate("sign2text/$code") },
                onVoice2Text = { code -> navController.navigate("chat/$code/sender") }
            )
        }

        // ── Chat (Voice2Text) ──────────────────────────────────────────────
        composable(
            route = "chat/{roomCode}/{role}",
            arguments = listOf(
                navArgument("roomCode") { type = NavType.StringType },
                navArgument("role")     { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: ""
            val role     = backStackEntry.arguments?.getString("role")     ?: ""
            ChatScreen(
                roomCode = roomCode,
                role = role,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Sign2Text ──────────────────────────────────────────────────────
        composable(
            route = "sign2text/{roomCode}",
            arguments = listOf(navArgument("roomCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomCode = backStackEntry.arguments?.getString("roomCode") ?: ""
            Sign2TextScreen(
                roomCode = roomCode,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
