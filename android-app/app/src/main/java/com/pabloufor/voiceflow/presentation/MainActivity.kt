package com.pabloufor.voiceflow.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pabloufor.voiceflow.presentation.screen.chat.ChatScreen
import com.pabloufor.voiceflow.presentation.screen.settings.SettingsScreen
import com.pabloufor.voiceflow.presentation.theme.VoiceFlowTheme
import dagger.hilt.android.AndroidEntryPoint

private const val ROUTE_CHAT = "chat"
private const val ROUTE_SETTINGS = "settings"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()

            VoiceFlowTheme {
                NavHost(navController = navController, startDestination = ROUTE_CHAT) {
                    composable(ROUTE_CHAT) {
                        ChatScreen(
                            onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) },
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            onNavigateBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
