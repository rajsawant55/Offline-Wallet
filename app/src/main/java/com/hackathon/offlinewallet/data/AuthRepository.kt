package com.hackathon.offlinewallet.data

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.mindrot.jbcrypt.BCrypt
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val userDao: UserDao,
    private val walletDao: WalletDao,
    private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    fun getUser(email: String): Flow<User?> = userDao.getUser(email)

    fun getCurrentUserEmail(): String? {
        return firebaseAuth.currentUser?.email ?: runBlocking {
            userDao.getUser(firebaseAuth.currentUser?.email ?: "").firstOrNull()?.email
        }
    }

    suspend fun resolveIdentifierToEmail(identifier: String): Result<String> {
        return try {
            // Check if identifier koreader is an email
            if (android.util.Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
                val user = userDao.getUser(identifier).firstOrNull()
                if (user != null) {
                    return Result.success(identifier)
                }
            }
            // Check if identifier is a username
            val user = userDao.getUserByUsername(identifier).firstOrNull()
            if (user != null) {
                return Result.success(user.email)
            }
            // If online, check Firestore
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("username", identifier)
                .get()
                .await()
            val userDoc = querySnapshot.documents.firstOrNull()
            if (userDoc != null) {
                val email = userDoc.getString("email") ?: return Result.failure(Exception("Email not found for username"))
                return Result.success(email)
            }
            Result.failure(Exception("No user found for identifier"))
        } catch (e: Exception) {
            Log.e("AuthRepository", "Identifier resolution failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun register(email: String, username: String, password: String): Result<String> {
        return try {
            // Hash password
            val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

            // Register with Firebase
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: return Result.failure(Exception("Registration failed: User is null"))

            // Store user in Firestore
            val userData = hashMapOf(
                "email" to email,
                "username" to username
            )
            firestore.collection("users").document(firebaseUser.uid).set(userData).await()

            // Store locally
            val user = User(email = email, username = username, passwordHash = passwordHash, jwtToken = firebaseUser.getIdToken(false).await().token)
            userDao.insertUser(user)

            Result.success(email)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Registration failed: ${e.message}", e)
            Result.failure(Exception("Registration failed: ${e.message}"))
        }
    }

    suspend fun login(email: String, password: String, activity: FragmentActivity, onBiometricPrompt: (BiometricPrompt) -> Unit): Result<String> {
        return try {
            // Check local credentials for offline login
            val localUser = userDao.getUser(email).firstOrNull()
            if (localUser != null && BCrypt.checkpw(password, localUser.passwordHash)) {
                // Biometric authentication
                if (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onBiometricPrompt(BiometricPrompt(activity, ContextCompat.getMainExecutor(context), this))
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            throw Exception("Biometric authentication failed: $errString")
                        }
                    })
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Biometric Login")
                        .setSubtitle("Confirm your identity")
                        .setNegativeButtonText("Cancel")
                        .build()
                    prompt.authenticate(promptInfo)
                    return Result.success(email)
                }
                return Result.success(email)
            }

            // Online login with Firebase
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: return Result.failure(Exception("Login failed: User is null"))
            val token = firebaseUser.getIdToken(false).await().token
            // Fetch username from Firestore
            val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
            val username = userDoc.getString("username") ?: "Unknown"
            // Store or update user in Room
            val user = User(
                email = email,
                username = username,
                passwordHash = BCrypt.hashpw(password, BCrypt.gensalt()),
                jwtToken = token
            )
            userDao.insertUser(user)
            Result.success(email)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login failed: ${e.message}", e)
            Result.failure(Exception("Login failed: ${e.message}"))
        }
    }

    fun isLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null || runBlocking {
            userDao.getUser(firebaseAuth.currentUser?.email ?: "").firstOrNull() != null
        }
    }

    fun logout() {
        firebaseAuth.signOut()
        runBlocking {
            userDao.updateJwtToken(firebaseAuth.currentUser?.email ?: "", null)
        }
    }
}