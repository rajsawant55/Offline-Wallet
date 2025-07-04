package com.hackathon.offlinewallet.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val pendingWalletUpdateDao: PendingWalletUpdateDao,
    private val userDao: UserDao,
    private val walletTransactionDao: WalletTransactionDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val client = supabaseClientProvider.client

            // Sync pending wallet updates
            val pendingUpdates = pendingWalletUpdateDao.getAllPendingUpdates()
            for (update in pendingUpdates) {
                when (update.transactionType) {
                    "add" -> {
                        val existingWallet = client.from("wallets")
                            .select { filter { eq("email", update.email) } }
                            .decodeSingleOrNull<Map<String, Any>>()
                        if (existingWallet != null) {
                            val currentBalance = (existingWallet["balance"] as Number).toDouble()
                            client.from("wallets").update(
                                mapOf("balance" to currentBalance + update.amount)
                            ) { filter { eq("email", update.email) } }
                        } else {
                            val userId = client.from("users")
                                .select { filter { eq("email", update.email) } }
                                .decodeSingleOrNull<Map<String, Any>>()?.get("id") as? String
                                ?: continue
                            client.from("wallets").insert(
                                mapOf(
                                    "user_id" to userId,
                                    "email" to update.email,
                                    "balance" to update.amount
                                )
                            )
                        }
                    }
                    "send", "receive" -> {
                        val email = update.email
                        val relatedEmail = update.relatedEmail ?: continue
                        // Verify related user exists
                        val relatedUser = client.from("users")
                            .select { filter { eq("email", relatedEmail) } }
                            .decodeSingleOrNull<Map<String, Any>>()
                        if (relatedUser == null) {
                            android.util.Log.w("SyncWorker", "Related user $relatedEmail not found, skipping")
                            continue
                        }
                        // Update wallet
                        val existingWallet = client.from("wallets")
                            .select { filter { eq("email", email) } }
                            .decodeSingleOrNull<Map<String, Any>>()
                        if (existingWallet != null) {
                            val currentBalance = (existingWallet["balance"] as Number).toDouble()
                            client.from("wallets").update(
                                mapOf("balance" to currentBalance + update.amount)
                            ) { filter { eq("email", email) } }
                        } else {
                            val userId = client.from("users")
                                .select { filter { eq("email", email) } }
                                .decodeSingleOrNull<Map<String, Any>>()?.get("id") as? String
                                ?: continue
                            client.from("wallets").insert(
                                mapOf(
                                    "user_id" to userId,
                                    "email" to email,
                                    "balance" to update.amount
                                )
                            )
                        }
                    }
                }
                pendingWalletUpdateDao.deletePendingUpdate(update.updateId)
            }

            // Sync pending transactions
            val pendingTransactions = walletTransactionDao.getPendingTransactionsByUserId(
                userId = supabaseClientProvider.client.auth.currentUserOrNull()?.id ?: ""
            )
            for (transaction in pendingTransactions) {
                val relatedEmail = if (transaction.type == "send") transaction.receiverEmail else transaction.senderEmail
                val relatedUser = client.from("users")
                    .select { filter { eq("email", relatedEmail) } }
                    .decodeSingleOrNull<Map<String, Any>>()
                if (relatedUser == null) {
                    android.util.Log.w("SyncWorker", "Related user $relatedEmail not found, skipping transaction ${transaction.id}")
                    continue
                }

                // Update wallet
                val email = if (transaction.type == "send") transaction.senderEmail else transaction.receiverEmail
                val amount = if (transaction.type == "send") -transaction.amount else transaction.amount
                val existingWallet = client.from("wallets")
                    .select { filter { eq("email", email) } }
                    .decodeSingleOrNull<Map<String, Any>>()
                if (existingWallet != null) {
                    val currentBalance = (existingWallet["balance"] as Number).toDouble()
                    client.from("wallets").update(
                        mapOf("balance" to currentBalance + amount)
                    ) { filter { eq("email", email) } }
                } else {
                    val userId = client.from("users")
                        .select { filter { eq("email", email) } }
                        .decodeSingleOrNull<Map<String, Any>>()?.get("id") as? String
                        ?: continue
                    client.from("wallets").insert(
                        mapOf(
                            "user_id" to userId,
                            "email" to email,
                            "balance" to amount
                        )
                    )
                }

                // Insert transaction to Supabase
                client.from("transactions").insert(
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
                )
                // Update local transaction status
                walletTransactionDao.insertTransaction(
                    transaction.copy(status = "completed")
                )
            }

            // Sync offline users (skip due to password issue)
            val offlineUsers = userDao.getAllUsers().filter { it.id.startsWith("offline_") }
            for (user in offlineUsers) {
                android.util.Log.w("SyncWorker", "Skipping user sync for ${user.email}: Password not available")
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}