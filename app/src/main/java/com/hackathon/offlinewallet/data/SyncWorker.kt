package com.hackathon.offlinewallet.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    @Assisted private val pendingWalletUpdateDao: PendingWalletUpdateDao,
    @Assisted private val userDao: UserDao,
    @Assisted private val walletDao: WalletDao,
    @Assisted private val walletTransactionDao: WalletTransactionDao,
    @Assisted private val firebaseAuth: FirebaseAuth,
    @Assisted private val firestore: FirebaseFirestore
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "SyncWorker"
    }

    init {
        android.util.Log.d("SyncWorker", "SyncWorker instantiated with Firebase")
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("SyncWorker", "Starting sync process")
            val currentUserId = firebaseAuth.currentUser?.uid ?: ""

//            // Sync offline users
//            val offlineUsers = userDao.getPendingUsers()
//            for (user in offlineUsers) {
//                if (user.passwordHash == null) {
//                    android.util.Log.w("SyncWorker", "Skipping user sync for ${user.email}: no password")
//                    continue
//                }
//                val authResult = firebaseAuth.createUserWithEmailAndPassword(user.email, user.passwordHash).await()
//                val newUserId = authResult.user?.uid ?: continue
//                firestore.collection("users").document(newUserId).set(
//                    mapOf(
//                        "user_id" to newUserId,
//                        "email" to user.email,
//                        "user_name" to user.username,
//                        "created_at" to user.createdAt
//                    )
//                ).await()
//                val wallet = walletDao.getWallet(user.id)
//                if (wallet != null) {
//                    firestore.collection("wallets").document(newUserId).set(
//                        mapOf(
//                            "user_id" to newUserId,
//                            "email" to user.email,
//                            "balance" to wallet.balance,
//                            "created_at" to wallet.createdAt,
//                            "updated_at" to wallet.updatedAt
//                        )
//                    ).await()
//                    walletDao.markWalletSynced(user.id, newUserId, newUserId)
//                }
//                userDao.markUserSynced(user.id, newUserId)
//                android.util.Log.d("SyncWorker", "Synced user ${user.email} with Firebase ID: $newUserId")
//            }

            // Sync pending wallet updates
            val pendingUpdates = pendingWalletUpdateDao.getAllPendingUpdates()
            for (update in pendingUpdates) {
                when (update.transactionType) {
                    "add" -> {
                        val snapshot = firestore.collection("wallets")
                            .whereEqualTo("email", update.email)
                            .get()
                            .await()
                        if (snapshot.documents.isNotEmpty()) {
                            val doc = snapshot.documents.first()
                            val currentBalance = doc.getDouble("balance") ?: 0.0
                            firestore.collection("wallets")
                                .document(doc.id)
                                .update("balance", currentBalance + update.amount)
                                .await()
                        } else {
                            val userSnapshot = firestore.collection("users")
                                .whereEqualTo("email", update.email)
                                .get()
                                .await()
                            if (userSnapshot.isEmpty) {
                                android.util.Log.w("SyncWorker", "User ${update.email} not found, skipping")
                                continue
                            }
                            val userId = userSnapshot.documents.first().getString("user_id") ?: ""
                            firestore.collection("wallets").document(userId).set(
                                mapOf(
                                    "user_id" to userId,
                                    "email" to update.email,
                                    "balance" to update.amount,
                                    "created_at" to OffsetDateTime.now().toString(),
                                    "updated_at" to OffsetDateTime.now().toString()
                                )
                            ).await()
                        }
                    }
                    "send", "receive" -> {
                        val email = update.email
                        val relatedEmail = update.relatedEmail ?: continue
                        val relatedUserSnapshot = firestore.collection("users")
                            .whereEqualTo("email", relatedEmail)
                            .get()
                            .await()
                        if (relatedUserSnapshot.isEmpty) {
                            android.util.Log.w("SyncWorker", "Related user $relatedEmail not found, skipping")
                            continue
                        }
                        val snapshot = firestore.collection("wallets")
                            .whereEqualTo("email", email)
                            .get()
                            .await()
                        if (snapshot.documents.isNotEmpty()) {
                            val doc = snapshot.documents.first()
                            val currentBalance = doc.getDouble("balance") ?: 0.0
                            firestore.collection("wallets")
                                .document(doc.id)
                                .update("balance", currentBalance + update.amount)
                                .await()
                        } else {
                            val userSnapshot = firestore.collection("users")
                                .whereEqualTo("email", email)
                                .get()
                                .await()
                            if (userSnapshot.isEmpty) {
                                android.util.Log.w("SyncWorker", "User $email not found, skipping")
                                continue
                            }
                            val userId = userSnapshot.documents.first().getString("user_id") ?: ""
                            firestore.collection("wallets").document(userId).set(
                                mapOf(
                                    "user_id" to userId,
                                    "email" to email,
                                    "balance" to update.amount,
                                    "created_at" to OffsetDateTime.now().toString(),
                                    "updated_at" to OffsetDateTime.now().toString()
                                )
                            ).await()
                        }
                    }
                }
                pendingWalletUpdateDao.deletePendingUpdate(update.updateId)
            }

            // Sync pending transactions
            val pendingTransactions = walletTransactionDao.getPendingTransactionsByUserId(currentUserId)
            for (transaction in pendingTransactions) {
                val relatedEmail = if (transaction.type == "send") transaction.receiverEmail else transaction.senderEmail
                val relatedUserSnapshot = firestore.collection("users")
                    .whereEqualTo("email", relatedEmail)
                    .get()
                    .await()
                if (relatedUserSnapshot.isEmpty) {
                    android.util.Log.w("SyncWorker", "Related user $relatedEmail not found, skipping transaction ${transaction.id}")
                    continue
                }
                val email = if (transaction.type == "send") transaction.senderEmail else transaction.receiverEmail
                val amount = if (transaction.type == "send") -transaction.amount else transaction.amount
                val snapshot = firestore.collection("wallets")
                    .whereEqualTo("email", email)
                    .get()
                    .await()
                if (snapshot.documents.isNotEmpty()) {
                    val doc = snapshot.documents.first()
                    val currentBalance = doc.getDouble("balance") ?: 0.0
                    firestore.collection("wallets")
                        .document(doc.id)
                        .update("balance", currentBalance + amount)
                        .await()
                } else {
                    val userSnapshot = firestore.collection("users")
                        .whereEqualTo("email", email)
                        .get()
                        .await()
                    if (userSnapshot.isEmpty) {
                        android.util.Log.w("SyncWorker", "User $email not found, skipping")
                        continue
                    }
                    val userId = userSnapshot.documents.first().getString("user_id") ?: ""
                    firestore.collection("wallets").document(userId).set(
                        mapOf(
                            "user_id" to userId,
                            "email" to email,
                            "balance" to amount,
                            "created_at" to OffsetDateTime.now().toString(),
                            "updated_at" to OffsetDateTime.now().toString()
                        )
                    ).await()
                }
                firestore.collection("transactions").document(transaction.id).set(
                    mapOf(
                        "id" to transaction.id,
                        "user_id" to transaction.userId,
                        "sender_email" to transaction.senderEmail,
                        "receiver_email" to transaction.receiverEmail,
                        "amount" to transaction.amount,
                        "type" to transaction.type,
                        "timestamp" to transaction.timestamp,
                        "status" to "completed"
                    )
                ).await()
                walletTransactionDao.insertTransaction(
                    transaction.copy(status = "completed")
                )
            }

            android.util.Log.d("SyncWorker", "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}