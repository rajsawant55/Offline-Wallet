package com.hackathon.offlinewallet.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.android.identity.util.UUID
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject

class WalletRepository @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
    private val walletDao: WalletDao,
    private val pendingWalletUpdateDao: PendingWalletUpdateDao,
    private val transactionDao: WalletTransactionDao,
    private val context: Context
) {

    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


    suspend fun getWallet(email: String): Result<Wallet?> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {
                println("User email $email")
                val response = supabaseClientProvider.client.from("wallets")
                    .select { filter { eq("email", email) } }
                    .decodeSingleOrNull<SWallet>()

                val wallet = response?.let {
                    Wallet(response.user_id, balance = (response.balance as Number).toDouble(), response.email)
                }
                if (wallet != null) {

                    walletDao.insertWallet(
                        LocalWallet(
                            id = response.user_id,
                            userId = response.user_id,
                            email = email,
                            balance = wallet.balance,
                            createdAt = response.created_at.toString(),
                            updatedAt = response.updated_at.toString()
                        )
                    )
                }
                Result.success(wallet)
            } else {
                val localWallet = walletDao.getWalletByEmail(email)
                Result.success(localWallet?.let { Wallet(it.userId, balance = it.balance, it.email) })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMoney(email: String, amount: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {
                val client = supabaseClientProvider.client
                val existingWallet = client.from("wallets")
                    .select { filter { eq("email", email) } }
                    .decodeSingleOrNull<SWallet>()

                if (existingWallet != null) {
                    val currentBalance = (existingWallet.balance as Number).toDouble()
                    client.from("wallets").update(
                        mapOf("balance" to currentBalance + amount)
                    ) { filter { eq("email", email) } }
                    walletDao.insertWallet(
                        LocalWallet(
                            id = existingWallet.user_id ,
                            userId = existingWallet.user_id,
                            email = email,
                            balance = currentBalance + amount,
                            createdAt = existingWallet.created_at as String,
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                } else {
                    val userId = client.auth.currentUserOrNull()?.id
                        ?: return@withContext Result.failure(IllegalStateException("User not authenticated"))
                    client.from("wallets").insert(
                        mapOf(
                            "user_id" to userId,
                            "email" to email,
                            "balance" to amount
                        )
                    )
                    val newWallet = client.from("wallets")
                        .select { filter { eq("email", email) } }
                        .decodeSingle<SWallet>()
                    walletDao.insertWallet(
                        LocalWallet(
                            id = newWallet.user_id as String,
                            userId = userId,
                            email = email,
                            balance = amount,
                            createdAt = newWallet.created_at as String,
                            updatedAt = newWallet.updated_at as String
                        )
                    )
                }
                val workRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
                Result.success(Unit)
            } else {
                val localWallet = walletDao.getWalletByEmail(email)
                if (localWallet != null) {
                    walletDao.insertWallet(
                        localWallet.copy(balance = localWallet.balance + amount, updatedAt = OffsetDateTime.now().toString())
                    )
                } else {
                    walletDao.insertWallet(
                        LocalWallet(
                            id = "offline_${email.hashCode()}",
                            userId = "offline_${email.hashCode()}",
                            email = email,
                            balance = amount,
                            createdAt = OffsetDateTime.now().toString(),
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                }
                pendingWalletUpdateDao.insertPendingUpdate(
                    PendingWalletUpdate(
                        email = email,
                        amount = amount,
                        timestamp = OffsetDateTime.now().toString(),
                        transactionType = "add",
                        relatedEmail = email
                    )
                )
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMoney(senderEmail: String, receiverEmail: String, amount: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (amount <= 0) return@withContext Result.failure(IllegalArgumentException("Amount must be positive"))
            if (senderEmail == receiverEmail) return@withContext Result.failure(IllegalArgumentException("Cannot send money to self"))
            val userId = supabaseClientProvider.client.auth.currentUserOrNull()?.id
                ?: "offline_${senderEmail.hashCode()}"
            val transactionId = UUID.randomUUID().toString()
            val timestamp = OffsetDateTime.now().toString()

            if (isOnline()) {
                val client = supabaseClientProvider.client
                // Check sender wallet
                val senderWallet = client.from("wallets")
                    .select { filter { eq("email", senderEmail) } }
                    .decodeSingleOrNull<SWallet>()
                if (senderWallet == null) return@withContext Result.failure(IllegalStateException("Sender wallet not found"))
                val senderBalance = (senderWallet.balance as Number).toDouble()
                if (senderBalance < amount) return@withContext Result.failure(IllegalStateException("Insufficient balance"))

                // Update sender wallet
                client.from("wallets").update(
                    mapOf("balance" to senderBalance - amount)
                ) { filter { eq("email", senderEmail) } }
                walletDao.insertWallet(
                    LocalWallet(
                        id = senderWallet.user_id as String,
                        userId = senderWallet.user_id as String,
                        email = senderEmail,
                        balance = senderBalance - amount,
                        createdAt = senderWallet.created_at as String,
                        updatedAt = OffsetDateTime.now().toString()
                    )
                )

                // Update or create receiver wallet
                val receiverWallet = client.from("wallets")
                    .select { filter { eq("email", receiverEmail) } }
                    .decodeSingleOrNull<SWallet>()
                if (receiverWallet != null) {
                    val receiverBalance = (receiverWallet.balance as Number).toDouble()
                    client.from("wallets").update(
                        mapOf("balance" to receiverBalance + amount)
                    ) { filter { eq("email", receiverEmail) } }
                    walletDao.insertWallet(
                        LocalWallet(
                            id = receiverWallet.user_id as String,
                            userId = receiverWallet.user_id as String,
                            email = receiverEmail,
                            balance = receiverBalance + amount,
                            createdAt = receiverWallet.created_at as String,
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                } else {
                    val receiverUser = client.from("users")
                        .select { filter { eq("email", receiverEmail) } }
                        .decodeSingleOrNull<Map<String, Any>>()
                    if (receiverUser == null) return@withContext Result.failure(IllegalStateException("Receiver not found"))
                    val receiverUserId = receiverUser["id"] as String
                    client.from("wallets").insert(
                        mapOf(
                            "user_id" to receiverUserId,
                            "email" to receiverEmail,
                            "balance" to amount
                        )
                    )
                    val newWallet = client.from("wallets")
                        .select { filter { eq("email", receiverEmail) } }
                        .decodeSingle<SWallet>()
                    walletDao.insertWallet(
                        LocalWallet(
                            id = newWallet.user_id as String,
                            userId = receiverUserId,
                            email = receiverEmail,
                            balance = amount,
                            createdAt = newWallet.created_at as String,
                            updatedAt = newWallet.updated_at as String
                        )
                    )
                }
                // Insert transactions for both sender and receiver
                client.from("transactions").insert(
                    mapOf(
                        "id" to transactionId,
                        "user_id" to userId,
                        "sender_email" to senderEmail,
                        "receiver_email" to receiverEmail,
                        "amount" to amount,
                        "type" to "send",
                        "timestamp" to timestamp,
                        "status" to "completed"
                    )
                )
                client.from("transactions").insert(
                    mapOf(
                        "id" to UUID.randomUUID().toString(),
                        "user_id" to receiverWallet?.user_id,
                        "sender_email" to senderEmail,
                        "receiver_email" to receiverEmail,
                        "amount" to amount,
                        "type" to "receive",
                        "timestamp" to timestamp,
                        "status" to "completed"
                    )
                )
                transactionDao.insertTransaction(
                    WalletTransactions(
                        id = transactionId,
                        userId = userId,
                        senderEmail = senderEmail,
                        receiverEmail = receiverEmail,
                        amount = amount,
                        type = "send",
                        timestamp = timestamp,
                        status = "completed"
                    )
                )
                transactionDao.insertTransaction(
                    WalletTransactions(
                        id = UUID.randomUUID().toString(),
                        userId = receiverWallet?.user_id ?: "offline_${receiverEmail.hashCode()}",
                        senderEmail = senderEmail,
                        receiverEmail = receiverEmail,
                        amount = amount,
                        type = "receive",
                        timestamp = timestamp,
                        status = "completed"
                    )
                )

                val workRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
                Result.success(Unit)
            } else {
                // Offline: Update sender wallet
                val senderWallet = walletDao.getWalletByEmail(senderEmail)
                    ?: return@withContext Result.failure(IllegalStateException("Sender wallet not found"))
                if (senderWallet.balance < amount) return@withContext Result.failure(IllegalStateException("Insufficient balance"))
                walletDao.insertWallet(
                    senderWallet.copy(balance = senderWallet.balance - amount, updatedAt = OffsetDateTime.now().toString())
                )

                // Offline: Update or create receiver wallet
                val receiverWallet = walletDao.getWalletByEmail(receiverEmail)
                if (receiverWallet != null) {
                    walletDao.insertWallet(
                        receiverWallet.copy(balance = receiverWallet.balance + amount, updatedAt = OffsetDateTime.now().toString())
                    )
                } else {
                    walletDao.insertWallet(
                        LocalWallet(
                            id = "offline_${receiverEmail.hashCode()}",
                            userId = "offline_${receiverEmail.hashCode()}",
                            email = receiverEmail,
                            balance = amount,
                            createdAt = OffsetDateTime.now().toString(),
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                }

                // Queue transaction for sync
                pendingWalletUpdateDao.insertPendingUpdate(
                    PendingWalletUpdate(
                        email = senderEmail,
                        amount = -amount, // Negative for sender
                        timestamp = OffsetDateTime.now().toString(),
                        transactionType = "send",
                        relatedEmail = receiverEmail
                    )
                )
                pendingWalletUpdateDao.insertPendingUpdate(
                    PendingWalletUpdate(
                        email = receiverEmail,
                        amount = amount, // Positive for receiver
                        timestamp = OffsetDateTime.now().toString(),
                        transactionType = "receive",
                        relatedEmail = senderEmail
                    )
                )

                // Insert transactions
                transactionDao.insertTransaction(
                    WalletTransactions(
                        id = transactionId,
                        userId = userId,
                        senderEmail = senderEmail,
                        receiverEmail = receiverEmail,
                        amount = amount,
                        type = "send",
                        timestamp = timestamp,
                        status = "pending"
                    )
                )
                transactionDao.insertTransaction(
                    WalletTransactions(
                        id = UUID.randomUUID().toString(),
                        userId = "offline_${receiverEmail.hashCode()}",
                        senderEmail = senderEmail,
                        receiverEmail = receiverEmail,
                        amount = amount,
                        type = "receive",
                        timestamp = timestamp,
                        status = "pending"
                    )
                )
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun receiveMoney(receiverEmail: String, senderEmail: String, amount: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (amount <= 0) return@withContext Result.failure(IllegalArgumentException("Amount must be positive"))
            if (senderEmail == receiverEmail) return@withContext Result.failure(IllegalArgumentException("Cannot receive money from self"))

            val userId = supabaseClientProvider.client.auth.currentUserOrNull()?.id
                ?: "offline_${receiverEmail.hashCode()}"
            val transactionId = UUID.randomUUID().toString()
            val timestamp = OffsetDateTime.now().toString()

            if (isOnline()) {
                val client = supabaseClientProvider.client
                // Verify sender exists
                val senderWallet = client.from("wallets")
                    .select { filter { eq("email", senderEmail) } }
                    .decodeSingleOrNull<SWallet>()
                if (senderWallet == null) return@withContext Result.failure(IllegalStateException("Sender not found"))
                val senderBalance = (senderWallet.balance as Number).toDouble()
                if (senderBalance < amount) return@withContext Result.failure(IllegalStateException("Sender has insufficient balance"))

                // Update sender wallet
                client.from("wallets").update(
                    mapOf("balance" to senderBalance - amount)
                ) { filter { eq("email", senderEmail) } }
                walletDao.insertWallet(
                    LocalWallet(
                        id = senderWallet.user_id as String,
                        userId = senderWallet.user_id as String,
                        email = senderEmail,
                        balance = senderBalance - amount,
                        createdAt = senderWallet.created_at as String,
                        updatedAt = OffsetDateTime.now().toString()
                    )
                )

                // Update or create receiver wallet
                val receiverWallet = client.from("wallets")
                    .select { filter { eq("email", receiverEmail) } }
                    .decodeSingleOrNull<SWallet>()
                if (receiverWallet != null) {
                    val receiverBalance = (receiverWallet.balance as Number).toDouble()
                    client.from("wallets").update(
                        mapOf("balance" to receiverBalance + amount)
                    ) { filter { eq("email", receiverEmail) } }
                    walletDao.insertWallet(
                        LocalWallet(
                            id = receiverWallet.user_id as String,
                            userId = receiverWallet.user_id as String,
                            email = receiverEmail,
                            balance = receiverBalance + amount,
                            createdAt = receiverWallet.created_at as String,
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                } else {
                    val receiverUser = client.from("users")
                        .select { filter { eq("email", receiverEmail) } }
                        .decodeSingleOrNull<Map<String, Any>>()
                    if (receiverUser == null) return@withContext Result.failure(IllegalStateException("Receiver not found"))
                    val receiverUserId = receiverUser["id"] as String
                    client.from("wallets").insert(
                        mapOf(
                            "user_id" to receiverUserId,
                            "email" to receiverEmail,
                            "balance" to amount
                        )
                    )
                    val newWallet = client.from("wallets")
                        .select { filter { eq("email", receiverEmail) } }
                        .decodeSingle<SWallet>()
                    walletDao.insertWallet(
                        LocalWallet(
                            id = newWallet.user_id as String,
                            userId = receiverUserId,
                            email = receiverEmail,
                            balance = amount,
                            createdAt = newWallet.created_at as String,
                            updatedAt = newWallet.updated_at as String
                        )
                    )
                }

                // Insert transactions
                client.from("transactions").insert(
                    mapOf(
                        "id" to transactionId,
                        "user_id" to userId,
                        "sender_email" to senderEmail,
                        "receiver_email" to receiverEmail,
                        "amount" to amount,
                        "type" to "receive",
                        "timestamp" to timestamp,
                        "status" to "completed"
                    )
                )
                client.from("transactions").insert(
                    mapOf(
                        "id" to UUID.randomUUID().toString(),
                        "user_id" to senderWallet.user_id,
                        "sender_email" to senderEmail,
                        "receiver_email" to receiverEmail,
                        "amount" to amount,
                        "type" to "send",
                        "timestamp" to timestamp,
                        "status" to "completed"
                    )
                )
                transactionDao.insertTransaction(
                    WalletTransactions(
                        id = transactionId,
                        userId = userId,
                        senderEmail = senderEmail,
                        receiverEmail = receiverEmail,
                        amount = amount,
                        type = "receive",
                        timestamp = timestamp,
                        status = "completed"
                    )
                )
                transactionDao.insertTransaction(
                    WalletTransactions(
                        id = UUID.randomUUID().toString(),
                        userId = senderWallet.user_id as String,
                        senderEmail = senderEmail,
                        receiverEmail = receiverEmail,
                        amount = amount,
                        type = "send",
                        timestamp = timestamp,
                        status = "completed"
                    )
                )

                val workRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
                Result.success(Unit)
            } else {
                // Offline: Update sender wallet
                val senderWallet = walletDao.getWalletByEmail(senderEmail)
                    ?: return@withContext Result.failure(IllegalStateException("Sender not found"))
                if (senderWallet.balance < amount) return@withContext Result.failure(IllegalStateException("Sender has insufficient balance"))
                walletDao.insertWallet(
                    senderWallet.copy(balance = senderWallet.balance - amount, updatedAt = OffsetDateTime.now().toString())
                )

                // Offline: Update or create receiver wallet
                val receiverWallet = walletDao.getWalletByEmail(receiverEmail)
                if (receiverWallet != null) {
                    walletDao.insertWallet(
                        receiverWallet.copy(balance = receiverWallet.balance + amount, updatedAt = OffsetDateTime.now().toString())
                    )
                } else {
                    walletDao.insertWallet(
                        LocalWallet(
                            id = "offline_${receiverEmail.hashCode()}",
                            userId = "offline_${receiverEmail.hashCode()}",
                            email = receiverEmail,
                            balance = amount,
                            createdAt = OffsetDateTime.now().toString(),
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                }

                // Queue transaction for sync
                pendingWalletUpdateDao.insertPendingUpdate(
                    PendingWalletUpdate(
                        email = senderEmail,
                        amount = -amount,
                        timestamp = OffsetDateTime.now().toString(),
                        transactionType = "send",
                        relatedEmail = receiverEmail
                    )
                )
                pendingWalletUpdateDao.insertPendingUpdate(
                    PendingWalletUpdate(
                        email = receiverEmail,
                        amount = amount,
                        timestamp = OffsetDateTime.now().toString(),
                        transactionType = "receive",
                        relatedEmail = senderEmail
                    )
                )

                // Insert transactions
                transactionDao.insertTransaction(
                    WalletTransactions(
                        id = transactionId,
                        userId = userId,
                        senderEmail = senderEmail,
                        receiverEmail = receiverEmail,
                        amount = amount,
                        type = "receive",
                        timestamp = timestamp,
                        status = "pending"
                    )
                )
                transactionDao.insertTransaction(
                    WalletTransactions(
                        id = UUID.randomUUID().toString(),
                        userId = "offline_${senderEmail.hashCode()}",
                        senderEmail = senderEmail,
                        receiverEmail = receiverEmail,
                        amount = amount,
                        type = "send",
                        timestamp = timestamp,
                        status = "pending"
                    )
                )
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTransactions(userId: String): Result<List<WalletTransactions>> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {
                val client = supabaseClientProvider.client
                val response = client.from("transactions")
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<Map<String, Any>>()
                val transactions = response.map {
                    WalletTransactions(
                        id = it["id"] as String,
                        userId = it["user_id"] as String,
                        senderEmail = it["sender_email"] as String,
                        receiverEmail = it["receiver_email"] as String,
                        amount = (it["amount"] as Number).toDouble(),
                        type = it["type"] as String,
                        timestamp = it["timestamp"] as String,
                        status = it["status"] as String
                    )
                }
                transactions.forEach { transactionDao.insertTransaction(it) }
                Result.success(transactions)
            } else {
                val transactions = transactionDao.getTransactionsByUserId(userId)
                Result.success(transactions)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun storeOfflineTransaction(transaction: WalletTransactions) {
        transactionDao.insertTransaction(transaction)
        val senderWallet = walletDao.getWalletByEmail(transaction.senderEmail)
        if (senderWallet != null && senderWallet.balance >= transaction.amount) {
            walletDao.insertWallet(
                senderWallet.copy(
                    balance = senderWallet.balance - transaction.amount,
                    updatedAt = OffsetDateTime.now().toString()
                )
            )
        }
        val receiverWallet = walletDao.getWalletByEmail(transaction.receiverEmail)
        if (receiverWallet != null) {
            walletDao.insertWallet(
                receiverWallet.copy(
                    balance = receiverWallet.balance + transaction.amount,
                    updatedAt = OffsetDateTime.now().toString()
                )
            )
        } else {
            walletDao.insertWallet(
                LocalWallet(
                    id = "offline_${transaction.receiverEmail.hashCode()}",
                    userId = "offline_${transaction.receiverEmail.hashCode()}",
                    email = transaction.receiverEmail,
                    balance = transaction.amount,
                    createdAt = OffsetDateTime.now().toString(),
                    updatedAt = OffsetDateTime.now().toString()
                )
            )
        }
        pendingWalletUpdateDao.insertPendingUpdate(
            PendingWalletUpdate(
                email = transaction.senderEmail,
                amount = -transaction.amount,
                timestamp = transaction.timestamp,
                transactionType = "send",
                relatedEmail = transaction.receiverEmail
            )
        )
        pendingWalletUpdateDao.insertPendingUpdate(
            PendingWalletUpdate(
                email = transaction.receiverEmail,
                amount = transaction.amount,
                timestamp = transaction.timestamp,
                transactionType = "receive",
                relatedEmail = transaction.senderEmail
            )
        )
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }


//
//    suspend fun syncPendingTransactions() {
//        if (!isOnline()) {
//            Log.d("WalletRepository", "Offline: cannot sync pending transactions")
//            return
//        }
//        val pendingUpdates = withContext(Dispatchers.IO) {
//            pendingWalletUpdateDao.getPendingUpdates()
//        }
//        for (update in pendingUpdates) {
//            try {
//                supabase.from("wallets").update({ set("balance", update.newBalance) }) {
//                    filter { eq("userId", update.userId) }
//                }
//                withContext(Dispatchers.IO) {
//                    pendingWalletUpdateDao.delete(update)
//                }
//            } catch (e: Exception) {
//                Log.e("WalletRepository", "Error syncing wallet update: ${e.message}", e)
//            }
//        }
//
//        val pendingTransactions = withContext(Dispatchers.IO) {
//            walletTransactionDao.getPendingTransactions()
//        }
//        for (transaction in pendingTransactions) {
//            try {
//                supabase.from("transactions").insert(transaction)
//                withContext(Dispatchers.IO) {
//                    walletTransactionDao.updateStatus(transaction.id, "completed")
//                }
//            } catch (e: Exception) {
//                Log.e("WalletRepository", "Error syncing transaction: ${e.message}", e)
//            }
//        }
//    }
}