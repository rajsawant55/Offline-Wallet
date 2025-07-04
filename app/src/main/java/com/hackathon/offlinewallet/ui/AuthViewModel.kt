package com.hackathon.offlinewallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.offlinewallet.data.AuthRepository
import com.hackathon.offlinewallet.data.SupabaseClientProvider
import com.hackathon.offlinewallet.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider, private val authRepository: AuthRepository
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    public fun getSupabaseClient():  SupabaseClientProvider {
        return supabaseClientProvider
    }
    init {
        // Check for existing session
        viewModelScope.launch {
            val session = supabaseClientProvider.client.auth.currentSessionOrNull()
            if (session != null) {
                fetchUserData(session.user?.email ?: "")
            }
        }
    }

    fun signUp(email: String, password: String, username: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.signUp(email, password, username)
                fetchUserData(email)
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    fun login(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.login(email, password)
                fetchUserData(email)
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    fun signOut(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                supabaseClientProvider.client.auth.signOut()
                _user.value = null
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun getCurrentUserEmail(): String? {
        return supabaseClientProvider.client.auth.currentUserOrNull()?.email
    }

    fun getUser(email: String): StateFlow<User?> {
        viewModelScope.launch {
            fetchUserData(email)
        }
        return user
    }

    private suspend fun fetchUserData(email: String) {
        try {
            val response = supabaseClientProvider.client.from("users")
                .select {
                    filter { eq("email", email) }
                }
                .decodeSingleOrNull<Map<String, Any>>()
            response?.let {
                _user.value = User(
                    id = it["id"] as? String ?: "",
                    email = it["email"] as? String ?: "",
                    username = it["username"] as? String ?: ""
                )
            }
        } catch (e: Exception) {
            _user.value = null
        }
    }
}
