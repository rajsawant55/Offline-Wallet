package com.hackathon.offlinewallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.offlinewallet.data.AuthRepository
import com.hackathon.offlinewallet.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    init {
        viewModelScope.launch {
            val sessionResult = authRepository.getCurrentSession()
            if(sessionResult!=null){
                fetchUserData(sessionResult)
            }
        }
    }

    fun signUp(email: String, password: String, username: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.signUp(email, password, username)
            if (result.isSuccess) {
                // Don't fetch user data immediately; wait for email confirmation
                android.util.Log.d("AuthViewModel", "Signup initiated for $email, awaiting email confirmation")
                onResult(true, null)
            } else {
                android.util.Log.e("AuthViewModel", "Signup failed: ${result.exceptionOrNull()?.message}")
                onResult(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun login(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                var fetchedEmail: String? = null
                if(authRepository.isOnline()){
                    fetchedEmail = authRepository.getCurrentUserEmail()
                } else {
                    fetchedEmail = email
                }
                if (fetchedEmail != null) {
                    fetchUserData(fetchedEmail)
                    android.util.Log.d("AuthViewModel", "Login successful, email: $email")
                    onResult(true, null)
                } else {
                    android.util.Log.w("AuthViewModel", "Login succeeded but no email found")
                    onResult(false, "Failed to retrieve user email")
                }
            } else {
                android.util.Log.e("AuthViewModel", "Login failed: ${result.exceptionOrNull()?.message}")
                onResult(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun signOut(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.signOut()
            if (result.isSuccess) {
                _user.value = null
                android.util.Log.d("AuthViewModel", "Signout successful")
                onResult(true)
            } else {
                android.util.Log.e("AuthViewModel", "Signout failed: ${result.exceptionOrNull()?.message}")
                onResult(false)
            }
        }
    }

    fun getCurrentUserEmail(): String? {
        return _user.value?.email
    }

    fun getUser(email: String): StateFlow<User?> {
        viewModelScope.launch {
            fetchUserData(email)
        }
        return user
    }

    private suspend fun fetchUserData(email: String) {
        val result = authRepository.getUser(email)
        if (result.isSuccess) {
            _user.value = result.getOrNull()
            android.util.Log.d("AuthViewModel", "User fetched: ${_user.value?.email}")
        } else {
            android.util.Log.e("AuthViewModel", "Failed to fetch user: ${result.exceptionOrNull()?.message}")
            _user.value = null
        }
    }
}