package com.voicechat.android.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.tasks.Task
import com.voicechat.android.R
import com.voicechat.android.domain.model.AuthState
import com.voicechat.android.presentation.auth.AuthViewModel
import com.voicechat.android.presentation.navigation.Screen
import com.voicechat.android.presentation.navigation.VoiceChatNavigation
import com.voicechat.android.presentation.theme.VoiceChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleSignInResult(task)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            VoiceChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val authState by authViewModel.authState.collectAsState()

                    // Check if already signed in
                    LaunchedEffect(Unit) {
                        val account = GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                        if (account != null && account.idToken != null) {
                            authViewModel.signInWithGoogle(account.idToken!!)
                        } else if (authState is AuthState.Unauthenticated) {
                            // Launch sign-in flow
                            launchGoogleSignInComposable()
                        }
                    }

                    // Navigate based on auth state
                    LaunchedEffect(authState) {
                        when (authState) {
                            is AuthState.Authenticated -> {
                                navController.navigate(Screen.Chat.route) {
                                    popUpTo(Screen.Auth.route) { inclusive = true }
                                }
                            }
                            is AuthState.Unauthenticated -> {
                                // Only navigate to auth if we're not already there
                                if (navController.currentDestination?.route != Screen.Auth.route) {
                                    navController.navigate(Screen.Auth.route) {
                                        popUpTo(Screen.Chat.route) { inclusive = true }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    @Composable
                    private fun launchGoogleSignInComposable() {
                        launchGoogleSignIn()
                    }

                    VoiceChatNavigation(
                        navController = navController,
                        startDestination = Screen.Auth.route
                    )
                }
            }
        }
    }

    private fun launchGoogleSignIn() {
        val signInIntent = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.web_client_id))
                .requestEmail()
                .requestProfile()
                .build()
        ).signInIntent
        
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            account.idToken?.let { idToken ->
                val authViewModel: AuthViewModel = hiltViewModel()
                authViewModel.signInWithGoogle(idToken)
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val statusCode = e.statusCode
            // Don't show error for cancelled sign-in (status code 12500)
            if (statusCode != GoogleSignInStatusCodes.CANCELED) {
                // Error handling can be done through authState
            }
        }
    }
}
