package com.hackathon.offlinewallet.ui

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.offlinewallet.data.AuthRepository
import com.hackathon.offlinewallet.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    fun getUser(email: String): StateFlow<User?> {
        return authRepository.getUser(email).stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    fun getCurrentUserEmail(): String? {
        return authRepository.getCurrentUserEmail()
    }

    fun resolveIdentifierToEmail(identifier: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val email = authRepository.resolveIdentifierToEmail(identifier)
            onResult(email)
        }
    }

    fun register(email: String, username: String, password: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.register(email, username, password)
            onResult(result)
        }
    }

    fun login(email: String, password: String, activity: FragmentActivity, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            authRepository.login(email, password, activity) { biometricPrompt ->
                // Handle biometric prompt if needed
            }.also { result ->
                onResult(result)
            }
        }
    }

    fun isLoggedIn(): Boolean {
        return authRepository.isLoggedIn()
    }

    fun logout() {
        authRepository.logout()
    }
}