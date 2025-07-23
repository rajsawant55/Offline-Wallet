package com.hackathon.offlinewallet.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
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
    @Assisted private val supabaseClientProvider: SupabaseClientProvider
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val client = supabaseClientProvider.client
            val currentUserId = client.auth.currentUserOrNull()?.id ?: ""

// Sync offline users
            val offlineUsers = userDao.getPendingUsers()
            for (user in offlineUsers) {
                if (user.passwordHash == null) {
                    android.util.Log.w("SyncWorker", "Skipping user sync for ${user.email}: no password")
                    continue
                }
                val signUpResponse = client.auth.signUpWith(Email) {
                    this.email = user.email
                    this.password = user.passwordHash // Note: Password handling needs secure storage
                }
                val newUserId = signUpResponse?.userMetadata?.get("sub")?.toString()?.replace("", "") ?: continue
                client.from("users").insert(
                    mapOf(
                        "user_id" to newUserId,
                        "email" to user.email,
                        "user_name" to user.username,
                        "created_at" to user.createdAt
                    )
                )
                val wallet = walletDao.getWallet(user.id)
                if (wallet != null) {
                    client.from("wallets").insert(
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
                android.util.Log.d("SyncWorker", "Synced user ${user.email} with new ID: $newUserId")
            }

// Sync pending wallet updates
            val pendingUpdates = pendingWalletUpdateDao.getAllPendingUpdates()
            for (update in pendingUpdates) {
                when (update.transactionType) {
                    "add" -> {
                        val existingWallet = client.from("wallets")
                            .select { filter { eq("email", update.email) } }
                            .decodeSingleOrNull<SWallet>()
                        if (existingWallet != null) {
                            val currentBalance = existingWallet.balance
                            client.from("wallets").update(
                                mapOf("balance" to currentBalance + update.amount)
                            ) { filter { eq("email", update.email) } }
                        } else {
                            val userId = client.from("users")
                                .select { filter { eq("email", update.email) } }
                            client.from("wallets").insert(
                                mapOf(
                                    "user_id" to userId,
                                    "email" to update.email,
                                    "balance" to update.amount,
                                    "created_at" to OffsetDateTime.now().toString(),
                                    "updated_at" to OffsetDateTime.now().toString()
                                )
                            )
                        }
                    }
                    "send", "receive" -> {
                        val email = update.email
                        val relatedEmail = update.relatedEmail ?: continue
                        val relatedUser = client.from("users")
                            .select { filter { eq("email", relatedEmail) } }
                            .decodeSingleOrNull<SUser>()
                        if (relatedUser == null) {
                            android.util.Log.w("SyncWorker", "Related user $relatedEmail not found, skipping")
                            continue
                        }
                        val existingWallet = client.from("wallets")
                            .select { filter { eq("email", email) } }
                            .decodeSingleOrNull<SWallet>()
                        if (existingWallet != null) {
                            val currentBalance = existingWallet.balance
                            client.from("wallets").update(
                                mapOf("balance" to currentBalance + update.amount)
                            ) { filter { eq("email", email) } }
                        } else {
                            val userId = client.from("users")
                                .select { filter { eq("email", email) } }
                                .decodeSingleOrNull<SUser>()
                            client.from("wallets").insert(
                                mapOf(
                                    "user_id" to userId,
                                    "email" to email,
                                    "balance" to update.amount,
                                    "created_at" to OffsetDateTime.now().toString(),
                                    "updated_at" to OffsetDateTime.now().toString()
                                )
                            )
                        }
                    }
                }
                pendingWalletUpdateDao.deletePendingUpdate(update.updateId)
            }

// Sync pending transactions
            val pendingTransactions = walletTransactionDao.getPendingTransactionsByUserId(currentUserId)
            for (transaction in pendingTransactions) {
                val relatedEmail = if (transaction.type == "send") transaction.receiverEmail else transaction.senderEmail
                val relatedUser = client.from("users")
                    .select { filter { eq("email", relatedEmail) } }
                    .decodeSingleOrNull<SUser>()
                if (relatedUser == null) {
                    android.util.Log.w("SyncWorker", "Related user $relatedEmail not found, skipping transaction ${transaction.id}")
                    continue
                }
                val email = if (transaction.type == "send") transaction.senderEmail else transaction.receiverEmail
                val amount = if (transaction.type == "send") -transaction.amount else transaction.amount
                val existingWallet = client.from("wallets")
                    .select { filter { eq("email", email) } }
                    .decodeSingleOrNull<SWallet>()
                if (existingWallet != null) {
                    val currentBalance = existingWallet.balance
                    client.from("wallets").update(
                        mapOf("balance" to currentBalance + amount)
                    ) { filter { eq("email", email) } }
                } else {
                    val userId = client.from("users")
                        .select { filter { eq("email", email) } }
                        .decodeSingleOrNull<SUser>()
                    client.from("wallets").insert(
                        mapOf(
                            "user_id" to userId,
                            "email" to email,
                            "balance" to amount,
                            "created_at" to OffsetDateTime.now().toString(),
                            "updated_at" to OffsetDateTime.now().toString()
                        )
                    )
                }
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
                walletTransactionDao.insertTransaction(
                    transaction.copy(status = "completed")
                )
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SyncWorker", "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}