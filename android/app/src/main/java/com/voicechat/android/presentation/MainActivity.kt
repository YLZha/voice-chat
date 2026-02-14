package com.voicechat.android.presentation

import android.os.Bundle
import android.util.Log
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
import com.voicechat.android.BuildConfig
import com.voicechat.android.R
import com.voicechat.android.domain.model.AuthState
import com.voicechat.android.presentation.auth.AuthViewModel
import com.voicechat.android.presentation.navigation.Screen
import com.voicechat.android.presentation.navigation.VoiceChatNavigation
import com.voicechat.android.presentation.theme.VoiceChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var authViewModelRef: AuthViewModel? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        authViewModelRef?.let { viewModel ->
            handleSignInResult(task, viewModel)
        }
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
                    authViewModelRef = authViewModel
                    val authState by authViewModel.authState.collectAsState()

                    // Check if already signed in
                    LaunchedEffect(Unit) {
                        val account = GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                        if (account != null && account.idToken != null) {
                            authViewModel.signInWithGoogle(account.idToken!!)
                        } else if (authState is AuthState.Unauthenticated) {
                            // Sign-in UI will be shown by VoiceChatNavigation
                        }
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
                .requestIdToken(BuildConfig.WEB_CLIENT_ID)
                .requestEmail()
                .requestProfile()
                .build()
        ).signInIntent
        
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(task: Task<GoogleSignInAccount>, viewModel: AuthViewModel) {
        try {
            Log.d(TAG, "Handling Google Sign-In result...")
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            
            if (account.idToken != null) {
                Log.d(TAG, "Google Sign-In successful, sending token to backend...")
                viewModel.signInWithGoogle(account.idToken!!)
            } else {
                Log.e(TAG, "Google Sign-In returned null idToken")
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val statusCode = e.statusCode
            Log.e(TAG, "Google Sign-In API Exception - Status: $statusCode, Message: ${e.message}")
            
            // Don't show error for cancelled sign-in (status code 12500)
            if (statusCode != GoogleSignInStatusCodes.CANCELED) {
                Log.e(TAG, "Sign-In error will be handled via authState")
                // Error handling can be done through authState
            } else {
                Log.d(TAG, "Sign-In was cancelled by user")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception during Google Sign-In", e)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
