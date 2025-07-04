package com.hackathon.offlinewallet.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
    private val userDao: UserDao,
    private val context: Context
) {

    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun signUp(email: String, password: String, username: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {
                supabaseClientProvider.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                val userId = supabaseClientProvider.client.auth.currentUserOrNull()?.id
                    ?: return@withContext Result.failure(Exception("User ID not found"))
                supabaseClientProvider.client.from("users").insert(
                    mapOf(
                        "id" to userId,
                        "email" to email,
                        "username" to username
                    )
                )
                userDao.insertUser(
                    LocalUser(
                        id = userId,
                        email = email,
                        username = username,
                        createdAt = OffsetDateTime.now().toString()
                    )
                )
                Result.success(Unit)
            } else {
                // Offline: Store user data locally with a placeholder ID
                userDao.insertUser(
                    LocalUser(
                        id = "offline_${email.hashCode()}",
                        email = email,
                        username = username,
                        createdAt = OffsetDateTime.now().toString()
                    )
                )
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {
                val signInResult = supabaseClientProvider.client.auth.signInWith(Email){
                    this.email = email
                    this.password = password
                }
                val user = getUser(email).getOrNull()
                if (user != null) {
                    userDao.insertUser(
                        LocalUser(
                            id = user.id,
                            email = user.email,
                            username = user.username,
                            createdAt = OffsetDateTime.now().toString()
                        )
                    )
                }
                Result.success(Unit)
            } else {
                // Offline login not supported; requires Supabase auth
                Result.failure(Exception("Login requires internet connection"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {
                supabaseClientProvider.client.auth.signOut()
            }
            // Clear local user data
            val email = getCurrentUserEmail()
            if (email != null) {
                userDao.deleteUser(email)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUserEmail(): String? {
        return supabaseClientProvider.client.auth.currentUserOrNull()?.email
    }

    suspend fun getUser(email: String): Result<User?> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {
                val response = supabaseClientProvider.client.from("users")
                    .select { filter { eq("email", email) } }
                    .decodeSingleOrNull<Map<String, Any>>()
                val user = response?.let {
                    User(
                        id = it["id"] as? String ?: "",
                        email = it["email"] as? String ?: "",
                        username = it["username"] as? String ?: ""
                    )
                }
                if (user != null) {
                    userDao.insertUser(
                        LocalUser(
                            id = user.id,
                            email = user.email,
                            username = user.username,
                            createdAt = OffsetDateTime.now().toString()
                        )
                    )
                }
                Result.success(user)
            } else {
                val localUser = userDao.getUserByEmail(email)
                Result.success(localUser?.let {
                    User(id = it.id, email = it.email, username = it.username)
                })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentSession(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val session = supabaseClientProvider.client.auth.currentSessionOrNull()
            Result.success(session != null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}