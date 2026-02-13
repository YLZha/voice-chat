package com.voicechat.android.presentation.auth

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.voicechat.android.R
import com.voicechat.android.domain.model.AuthState

private const val TAG = "AuthScreen"

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val isSigningIn by viewModel.isSigningIn.collectAsState()
    var debugStatus by remember { mutableStateOf("") }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Google Sign-In result received, resultCode=${result.resultCode}")
        debugStatus = "Google returned (code=${result.resultCode})"
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        handleSignInResult(task, viewModel) { status -> debugStatus = status }
    }

    LaunchedEffect(authState) {
        Log.d(TAG, "AuthState changed: $authState")
        when (authState) {
            is AuthState.Authenticated -> {
                debugStatus = "Authenticated! Navigating..."
                onAuthSuccess()
            }
            is AuthState.Error -> {
                debugStatus = "Error: ${(authState as AuthState.Error).message}"
            }
            is AuthState.Loading -> {
                debugStatus = "Contacting server..."
            }
            else -> {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Voice Chat",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sign in to start voice conversations",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (authState) {
                is AuthState.Error -> {
                    val error = (authState as AuthState.Error).message
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                else -> {}
            }

            // Debug status - shows exactly what's happening
            if (debugStatus.isNotEmpty()) {
                Text(
                    text = debugStatus,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    debugStatus = "Launching Google Sign-In..."
                    val signInIntent = GoogleSignIn.getClient(
                        context,
                        com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                        )
                            .requestIdToken(context.getString(R.string.web_client_id))
                            .requestEmail()
                            .requestProfile()
                            .build()
                    ).signInIntent

                    googleSignInLauncher.launch(signInIntent)
                },
                enabled = !isSigningIn && authState !is AuthState.Loading,
                modifier = Modifier.height(56.dp)
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sign_in),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Powered by Google",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

private fun handleSignInResult(
    task: Task<GoogleSignInAccount>,
    viewModel: AuthViewModel,
    onStatus: (String) -> Unit
) {
    try {
        val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
        val idToken = account.idToken
        val email = account.email
        Log.d(TAG, "Google account: $email, idToken null=${idToken == null}")
        if (idToken != null) {
            onStatus("Got token for $email, sending to server...")
            viewModel.signInWithGoogle(idToken)
        } else {
            Log.e(TAG, "Google Sign-In returned null idToken for $email")
            onStatus("Error: Google returned null ID token for $email")
        }
    } catch (e: com.google.android.gms.common.api.ApiException) {
        val statusCode = e.statusCode
        Log.e(TAG, "Google Sign-In failed: status=$statusCode", e)
        if (statusCode == GoogleSignInStatusCodes.CANCELED) {
            onStatus("Sign-in cancelled")
        } else {
            onStatus("Google Sign-In error: code $statusCode")
        }
    }
}
