package com.hackathon.offlinewallet.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.android.identity.util.UUID
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
    private val context: Context,
    private val walletDao: WalletDao
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
                android.util.Log.d("AuthRepository", "Initiating signup for $email")
                val signUpWithResponse = supabaseClientProvider.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                val tempString : String = signUpWithResponse?.userMetadata?.get("sub").toString()
                val uniqueId = tempString.replace("\"", "")
                supabaseClientProvider.client.from("users").insert(
                    mapOf(
                        "user_id" to uniqueId,
                        "email" to email,
                        "user_name" to username,
                        "created_at" to OffsetDateTime.now().toString()
                    )
                )

                supabaseClientProvider.client.from("wallets").insert(
                    mapOf(
                        "user_id" to uniqueId,
                        "email" to email,
                        "balance" to "0.00",
                        "created_at" to OffsetDateTime.now().toString(),
                        "updated_at" to OffsetDateTime.now().toString()
                    )
                )

                android.util.Log.d("AuthRepository", "User inserted into Supabase with ID: $uniqueId")
                userDao.insertUser(
                    LocalUser(
                        id = uniqueId,
                        email = email,
                        username = username,
                        createdAt = OffsetDateTime.now().toString()
                    )
                )
                walletDao.insertWallet(
                    LocalWallet(
                        id = uniqueId,
                        userId = username,
                        email = email,
                        balance = 0.0,
                        createdAt =  OffsetDateTime.now().toString(),
                        updatedAt = OffsetDateTime.now().toString()
                    )
                )


                Result.success(Unit)
            } else {
                val tempUserId = email.hashCode().toString()
                android.util.Log.d("AuthRepository", "Offline: storing user $email locally")
                userDao.insertUser(
                    LocalUser(
                        id = tempUserId,
                        email = email,
                        username = username,
                        createdAt = OffsetDateTime.now().toString()
                    )
                )
                walletDao.insertWallet(
                    LocalWallet(
                        id = tempUserId,
                        userId = username,
                        email = email,
                        balance = 0.0,
                        createdAt =  OffsetDateTime.now().toString(),
                        updatedAt = OffsetDateTime.now().toString()
                    )
                )
                Result.success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Signup failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {
                supabaseClientProvider.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
//                val user = getUser(email).getOrNull()
//                if (user != null) {
//                    android.util.Log.d("AuthRepository", "Logged in user: ${user.email}")
//                    userDao.insertUser(
//                        LocalUser(
//                            id = user.id,
//                            email = user.email,
//                            username = user.username,
//                            createdAt = OffsetDateTime.now().toString()
//                        )
//                    )
//                }
                Result.success(Unit)
            } else {
                android.util.Log.w("AuthRepository", "Offline login not supported")
                Result.failure(Exception("Login requires internet connection"))
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Login failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {
                supabaseClientProvider.client.auth.signOut()
            }
            val email = getCurrentUserEmail()
            if (email != null) {
                userDao.deleteUser(email)
            }
            android.util.Log.d("AuthRepository", "Signout successful")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Signout failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getCurrentUserEmail(): String? {
        return supabaseClientProvider.client.auth.currentUserOrNull()?.email
    }

    suspend fun getUser(email: String): Result<User?> = withContext(Dispatchers.IO) {
        try {
            println("User email during get User $email")
            if (isOnline()) {
                val response = supabaseClientProvider.client.from("users")
                    .select { filter { eq("email", email) } }
                    .decodeSingleOrNull<Map<String, Any>>()
                val user = response?.let {
                    User(
                        id = it["id"] as? String ?: "",
                        email = it["email"] as? String ?: "",
                        username = it["user_name"] as? String ?: "",
                        createdAt = it["created_at"] as? String ?: ""
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
                    User(id = it.id, email = it.email, username = it.username, createdAt = it.createdAt)
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Failed to fetch user: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getCurrentSession(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val session = supabaseClientProvider.client.auth.currentSessionOrNull()
            Result.success(session != null)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Failed to check session: ${e.message}", e)
            Result.failure(e)
        }
    }
}