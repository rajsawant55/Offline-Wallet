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
import java.security.MessageDigest
import java.time.OffsetDateTime
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
    private val userDao: UserDao,
    private val context: Context,
    private val walletDao: WalletDao
) {

    public fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun String.toSHA256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this.toByteArray())
            .joinToString("") { "%02x".format(it) }
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
                        createdAt = OffsetDateTime.now().toString(),
                        needsSync = false,
                        passwordHash = password.toSHA256()
                    )
                )
                walletDao.insertWallet(
                    LocalWallet(
                        id = uniqueId,
                        userId = uniqueId,
                        email = email,
                        balance = 0.0,
                        createdAt =  OffsetDateTime.now().toString(),
                        updatedAt = OffsetDateTime.now().toString(),
                        needsSync = false
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
                        createdAt = OffsetDateTime.now().toString(),
                        needsSync = true,
                        passwordHash = password.toSHA256()
                    )
                )
                walletDao.insertWallet(
                    LocalWallet(
                        id = tempUserId,
                        userId = tempUserId,
                        email = email,
                        balance = 0.0,
                        createdAt =  OffsetDateTime.now().toString(),
                        updatedAt = OffsetDateTime.now().toString(),
                        needsSync = true
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
                val user = getUser(email).getOrNull()
                if (user != null) {
                    android.util.Log.d("AuthRepository", "Logged in user: ${user.email}")
                    userDao.insertUser(
                        LocalUser(
                            id = user.id,
                            email = user.email,
                            username = user.username,
                            createdAt = user.createdAt,
                            needsSync = false,
                            passwordHash = password.toSHA256()
                        )
                    )
                    val wallet = supabaseClientProvider.client.from("wallets")
                        .select { filter { eq("user_id", user.id) } }
                        .decodeSingleOrNull<SWallet>()
                    if (wallet == null) {
                        supabaseClientProvider.client.from("wallets").insert(
                            mapOf(
                                "user_id" to user.id,
                                "email" to user.email,
                                "balance" to "0.00",
                                "created_at" to OffsetDateTime.now().toString(),
                                "updated_at" to OffsetDateTime.now().toString()
                            )
                        )
                        walletDao.insertWallet(
                            LocalWallet(
                                id = user.id,
                                userId = user.id,
                                email = user.email,
                                balance = 0.0,
                                createdAt = OffsetDateTime.now().toString(),
                                updatedAt = OffsetDateTime.now().toString(),
                                needsSync = false
                            )
                        )
                    }
                    syncOfflineData()
                }
                Result.success(Unit)
            } else {
                android.util.Log.d("AuthRepository", "Offline: checking local user for $email")
                val localUser = userDao.getUserByEmail(email)
                if (localUser != null && localUser.passwordHash == password.toSHA256()) {
                    android.util.Log.d("AuthRepository", "Offline login successful for ${localUser.email}")
                    Result.success(Unit)
                } else {
                    android.util.Log.w("AuthRepository", "Offline login failed: invalid credentials")
                    Result.failure(Exception("Invalid credentials for offline login"))
                }
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
                val postgrestResult = supabaseClientProvider.client.from("users")
                    .select { filter { eq("email", email) } }
                println("User email during get User $postgrestResult")
                val response = postgrestResult
                    .decodeSingleOrNull<SUser>()
                val user = response?.let {
                    User(
                        id = it.user_id,
                        email = it.email,
                        username = it.user_name,
                        createdAt = it.created_at
                    )
                }
//                if (user != null) {
//                    userDao.insertUser(
//                        LocalUser(
//                            id = user.id,
//                            email = user.email,
//                            username = user.username,
//                            createdAt = OffsetDateTime.now().toString()
//                        )
//                    )
//                }
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

    suspend fun syncOfflineData() = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            android.util.Log.d("AuthRepository", "Cannot sync: offline")
            return@withContext
        }
        try {
            val pendingUsers = userDao.getPendingUsers()
            android.util.Log.d("AuthRepository", "Syncing ${pendingUsers.size} pending users")
            for (user in pendingUsers) {
                if (user.passwordHash == null) {
                    android.util.Log.w("AuthRepository", "Skipping sync for ${user.email}: no password")
                    continue
                }
                val signUpResponse = supabaseClientProvider.client.auth.signUpWith(Email) {
                    this.email = user.email
                    this.password = user.passwordHash // Note: Password handling needs secure storage
                }
                val newUserId = signUpResponse?.userMetadata?.get("sub")?.toString()?.replace("\"", "") ?: continue
                supabaseClientProvider.client.from("users").insert(
                    mapOf(
                        "user_id" to newUserId,
                        "email" to user.email,
                        "user_name" to user.username,
                        "created_at" to user.createdAt
                    )
                )
                val wallet = walletDao.getWallet(user.id)
                if (wallet != null) {
                    supabaseClientProvider.client.from("wallets").insert(
                        mapOf(
                            "user_id" to newUserId,
                            "email" to user.email,
                            "balance" to wallet.balance.toString(),
                            "created_at" to wallet.createdAt,
                            "updated_at" to wallet.updatedAt
                        )
                    )
                    walletDao.markWalletSynced(user.id, newUserId, newUserId)
                }
                userDao.markUserSynced(user.id, newUserId)
                android.util.Log.d("AuthRepository", "Synced user ${user.email} with new ID: $newUserId")
            }
            android.util.Log.d("AuthRepository", "Offline data sync completed")
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Sync failed: ${e.message}", e)
        }
    }
}