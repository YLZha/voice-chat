package com.voicechat.android.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voicechat.android.presentation.auth.AuthScreen
import com.voicechat.android.presentation.chat.ChatScreen

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Chat : Screen("chat")
}

@Composable
fun VoiceChatNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Auth.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Chat.route) {
            ChatScreen(
                onSignOut = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Chat.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
