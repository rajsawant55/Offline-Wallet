package com.hackathon.offlinewallet.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.OffsetDateTime
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
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
                Log.d("AuthRepository", "Initiating signup for $email")

                val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                val userId = authResult.user?.uid ?: return@withContext Result.failure(IllegalStateException("User creation failed"))
                val userData = mapOf(
                    "user_id" to userId,
                    "email" to email,
                    "user_name" to username,
                    "created_at" to OffsetDateTime.now().toString()
                )
                firestore.collection("users").document(userId).set(userData).await()
                userDao.insertUser(
                    LocalUser(
                        id = userId,
                        email = email,
                        username = username,
                        passwordHash = password, // Note: Store securely in production
                        createdAt = OffsetDateTime.now().toString(),
                        needsSync = true
                    )
                )
                val walletData = mapOf(
                    "user_id" to userId,
                    "email" to email,
                    "balance" to 0.0,
                    "created_at" to OffsetDateTime.now().toString(),
                    "updated_at" to OffsetDateTime.now().toString()
                )
                firestore.collection("wallets").document(userId).set(walletData).await()
                walletDao.insertWallet(
                    LocalWallet(
                        id = userId,
                        userId = userId,
                        email = email,
                        balance = 0.0,
                        createdAt = OffsetDateTime.now().toString(),
                        updatedAt = OffsetDateTime.now().toString()
                    )
                )
                Result.success(Unit)
            } else {
                val tempUserId = email.hashCode().toString()
                Log.d("AuthRepository", "Offline: signup not supported")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Signup failed: ${e.message}", e)
            Result.failure(e)
        }
    }


    suspend fun login(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {
                val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val userId = authResult.user?.uid ?: return@withContext Result.failure(IllegalStateException("Sign-in failed"))
                val userSnapshot = firestore.collection("users")
                    .whereEqualTo("email", email)
                    .get()
                    .await()
                if (userSnapshot.isEmpty) return@withContext Result.failure(IllegalStateException("User not found"))
                val userDoc = userSnapshot.documents.first()
                userDao.insertUser(
                    LocalUser(
                        id = userId,
                        email = email,
                        username = userDoc.getString("user_name") ?: "",
                        passwordHash = password, // Note: Store securely in production
                        createdAt = userDoc.getString("created_at") ?: OffsetDateTime.now().toString(),
                        needsSync = true
                    )
                )

                walletDao.insertWallet(
                    LocalWallet(
                        id = userId,
                        userId = userId,
                        email = email,
                        balance = 0.0,
                        createdAt = OffsetDateTime.now().toString(),
                        updatedAt = OffsetDateTime.now().toString(),
                        needsSync = false
                    )
                )
                Result.success(Unit)
            } else {
                Log.d("AuthRepository", "Offline: checking local user for $email")
                val localUser = userDao.getUserByEmail(email)
                if (localUser != null && localUser.passwordHash == password.toSHA256()) {
                    Log.d("AuthRepository", "Offline login successful for ${localUser.email}")
                    Result.success(Unit)
                } else {
                    Log.w("AuthRepository", "Offline login failed: invalid credentials")
                    Result.failure(Exception("Invalid credentials for offline login"))
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {
                firebaseAuth.signOut()
            }
            Log.d("AuthRepository", "Signout successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Signout failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getCurrentUserEmail(): String? {
        return firebaseAuth.currentUser?.email
    }

    fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid

    suspend fun getUser(email: String): Result<User?> = withContext(Dispatchers.IO) {
        try {
            println("User email during get User $email")
            if (isOnline()) {

                val snapshot = firestore.collection("users")
                    .whereEqualTo("email", email)
                    .get()
                    .await()
                val user = snapshot.documents.firstOrNull()?.let { doc ->
                    User(
                        id = doc.getString("user_id") ?: "",
                        username = doc.getString("user_name") ?: "",
                        email = doc . getString ("email") ?: "",
                        createdAt = (doc.get("created_at") ?: "").toString(),
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
            Log.e("AuthRepository", "Failed to fetch user: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getCurrentSession(): String? = withContext(Dispatchers.IO) {
        firebaseAuth.currentUser?.email
    }


}