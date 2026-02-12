package com.voicechat.android.presentation.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicechat.android.domain.model.AuthState
import com.voicechat.android.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            try {
                _isSigningIn.value = true
                Log.d(TAG, "Starting Google Sign-In with token: ${idToken.substring(0, 20)}...")
                
                val result = authRepository.signInWithGoogle(idToken)
                
                if (result.isSuccess) {
                    Log.d(TAG, "Google Sign-In succeeded")
                } else {
                    Log.e(TAG, "Google Sign-In failed: ${result.exceptionOrNull()?.message}")
                }
                
                _isSigningIn.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Google Sign-In", e)
                _isSigningIn.value = false
                // Error is propagated via authState
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Signing out")
                authRepository.signOut()
                Log.d(TAG, "Sign-out completed")
            } catch (e: Exception) {
                Log.e(TAG, "Exception during sign-out", e)
            }
        }
    }

    companion object {
        private const val TAG = "AuthViewModel"
    }
}
