package com.hackathon.offlinewallet.data
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.android.identity.util.UUID
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import javax.inject.Inject

class WalletRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
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
                val snapshot = firestore.collection("wallets")
                    .whereEqualTo("email", email)
                    .get()
                    .await()
                val wallet = snapshot.documents.firstOrNull()?.let { doc ->
                    Wallet(
                        userId = doc.getString("user_id") ?: "",
                        balance = doc.getDouble("balance") ?: 0.0,
                        email = doc.getString("email") ?: ""
                    )
                }
                if (wallet != null) {
                    walletDao.insertWallet(
                        LocalWallet(
                            id = wallet.userId,
                            userId = wallet.userId,
                            email = wallet.email,
                            balance = wallet.balance,
                            createdAt = snapshot.documents.first().getString("created_at") ?: OffsetDateTime.now().toString(),
                            updatedAt = snapshot.documents.first().getString("updated_at") ?: OffsetDateTime.now().toString()
                        )
                    )
                }
                Result.success(wallet)
            } else {
                val localWallet = walletDao.getWalletByEmail(email)
                Result.success(localWallet?.let { Wallet(it.userId, it.balance, it.email) })
            }
        } catch (e: Exception) {
            Log.e("WalletRepository", "Error fetching wallet: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun addMoney(email: String, amount: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (amount <= 0) return@withContext Result.failure(IllegalArgumentException("Amount must be positive"))
            if (isOnline()) {
                val userId = firebaseAuth.currentUser?.uid
                    ?: return@withContext Result.failure(IllegalStateException("User not authenticated"))
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
                    walletDao.insertWallet(
                        LocalWallet(
                            id = doc.getString("user_id") ?: userId,
                            userId = userId,
                            email = email,
                            balance = currentBalance + amount,
                            createdAt = doc.getString("created_at") ?: OffsetDateTime.now().toString(),
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                } else {
                    val newWallet = mapOf(
                        "user_id" to userId,
                        "email" to email,
                        "balance" to amount,
                        "created_at" to OffsetDateTime.now().toString(),
                        "updated_at" to OffsetDateTime.now().toString()
                    )
                    firestore.collection("wallets").document(userId).set(newWallet).await()
                    walletDao.insertWallet(
                        LocalWallet(
                            id = userId,
                            userId = userId,
                            email = email,
                            balance = amount,
                            createdAt = OffsetDateTime.now().toString(),
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                }
                val workRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
                Log.d("WalletRepository", "Enqueuing SyncWorker for email: $email, amount: $amount")
                Result.success(Unit)
            } else {
//                val localWallet = walletDao.getWalletByEmail(email)
//                if (localWallet != null) {
//                    walletDao.insertWallet(
//                        localWallet.copy(balance = localWallet.balance + amount, updatedAt = OffsetDateTime.now().toString())
//                    )
//                } else {
//                    walletDao.insertWallet(
//                        LocalWallet(
//                            id = "offline_${email.hashCode()}",
//                            userId = "offline_${email.hashCode()}",
//                            email = email,
//                            balance = amount,
//                            createdAt = OffsetDateTime.now().toString(),
//                            updatedAt = OffsetDateTime.now().toString()
//                        )
//                    )
//                }
//                pendingWalletUpdateDao.insertPendingUpdate(
//                    PendingWalletUpdate(
//                        email = email,
//                        amount = amount,
//                        timestamp = OffsetDateTime.now().toString(),
//                        transactionType = "add",
//                        relatedEmail = email
//                    )
//                )
                Result.failure(IllegalStateException("Add Money disabled in Offline mode"))

            }
        } catch (e: Exception) {
            Log.e("WalletRepository", "Error adding money: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendMoney(senderEmail: String, receiverEmail: String, amount: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (amount <= 0) return@withContext Result.failure(IllegalArgumentException("Amount must be positive"))
            if (senderEmail == receiverEmail) return@withContext Result.failure(IllegalArgumentException("Cannot send money to self"))
            val userId = firebaseAuth.currentUser?.uid ?: "offline_${senderEmail.hashCode()}"
            val transactionId = UUID.randomUUID().toString()
            val timestamp = OffsetDateTime.now().toString()

            if (isOnline()) {
                val senderSnapshot = firestore.collection("wallets")
                    .whereEqualTo("email", senderEmail)
                    .get()
                    .await()
                if (senderSnapshot.isEmpty) return@withContext Result.failure(IllegalStateException("Sender wallet not found"))
                val senderDoc = senderSnapshot.documents.first()
                val senderBalance = senderDoc.getDouble("balance") ?: 0.0
                if (senderBalance < amount) return@withContext Result.failure(IllegalStateException("Insufficient balance"))

                firestore.collection("wallets")
                    .document(senderDoc.id)
                    .update("balance", senderBalance - amount)
                    .await()
                walletDao.insertWallet(
                    LocalWallet(
                        id = senderDoc.getString("user_id") ?: userId,
                        userId = userId,
                        email = senderEmail,
                        balance = senderBalance - amount,
                        createdAt = senderDoc.getString("created_at") ?: OffsetDateTime.now().toString(),
                        updatedAt = OffsetDateTime.now().toString()
                    )
                )

                val receiverSnapshot = firestore.collection("wallets")
                    .whereEqualTo("email", receiverEmail)
                    .get()
                    .await()
                if (!receiverSnapshot.isEmpty) {
                    val receiverDoc = receiverSnapshot.documents.first()
                    val receiverBalance = receiverDoc.getDouble("balance") ?: 0.0
                    firestore.collection("wallets")
                        .document(receiverDoc.id)
                        .update("balance", receiverBalance + amount)
                        .await()
                    walletDao.insertWallet(
                        LocalWallet(
                            id = receiverDoc.getString("user_id") ?: userId,
                            userId = userId,
                            email = receiverEmail,
                            balance = receiverBalance + amount,
                            createdAt = receiverDoc.getString("created_at") ?: OffsetDateTime.now().toString(),
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                } else {
                    val receiverUserSnapshot = firestore.collection("users")
                        .whereEqualTo("email", receiverEmail)
                        .get()
                        .await()
                    if (receiverUserSnapshot.isEmpty) return@withContext Result.failure(IllegalStateException("Receiver not found"))
                    val receiverUserId = receiverUserSnapshot.documents.first().getString("user_id") ?: ""
                    val newWallet = mapOf(
                        "user_id" to receiverUserId,
                        "email" to receiverEmail,
                        "balance" to amount,
                        "created_at" to OffsetDateTime.now().toString(),
                        "updated_at" to OffsetDateTime.now().toString()
                    )
                    firestore.collection("wallets").document(receiverUserId).set(newWallet).await()
                    walletDao.insertWallet(
                        LocalWallet(
                            id = receiverUserId,
                            userId = receiverUserId,
                            email = receiverEmail,
                            balance = amount,
                            createdAt = OffsetDateTime.now().toString(),
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                }

                firestore.collection("transactions").document(transactionId).set(
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
                ).await()

                val receiverWalletDoc = firestore.collection("wallets")
                    .whereEqualTo("email", receiverEmail)
                    .get()
                    .await()
                val receiverUserIdOnline = receiverWalletDoc.documents.firstOrNull()?.getString("user_id")
                    ?: firestore.collection("users").whereEqualTo("email", receiverEmail).get().await().documents.firstOrNull()?.getString("user_id")
                    ?: "offline_${receiverEmail.hashCode()}"

                firestore.collection("transactions").document(UUID.randomUUID().toString()).set(
                    mapOf(
                        "id" to UUID.randomUUID().toString(),
                        "user_id" to receiverUserIdOnline,
                        "sender_email" to senderEmail,
                        "receiver_email" to receiverEmail,
                        "amount" to amount,
                        "type" to "receive",
                        "timestamp" to timestamp,
                        "status" to "completed"
                    )
                ).await()
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
                        userId = receiverUserIdOnline,
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
                val senderWallet = walletDao.getWalletByEmail(senderEmail)
                    ?: return@withContext Result.failure(IllegalStateException("Sender wallet not found"))
                val currBalance : Double = senderWallet.balance
                if (currBalance <= amount) return@withContext Result.failure(IllegalStateException("Insufficient balance"))
                walletDao.insertWallet(
                    senderWallet.copy(balance = currBalance - amount, updatedAt = OffsetDateTime.now().toString())
                )

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
            Log.e("WalletRepository", "Error sending money: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun receiveMoney(receiverEmail: String, senderEmail: String, amount: Double): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (amount <= 0) return@withContext Result.failure(IllegalArgumentException("Amount must be positive"))
            if (senderEmail == receiverEmail) return@withContext Result.failure(IllegalArgumentException("Cannot receive money from self"))
            val userId = firebaseAuth.currentUser?.uid ?: "offline_${receiverEmail.hashCode()}"
            val transactionId = UUID.randomUUID().toString()
            val timestamp = OffsetDateTime.now().toString()

            if (isOnline()) {
                val senderSnapshot = firestore.collection("wallets")
                    .whereEqualTo("email", senderEmail)
                    .get()
                    .await()
                if (senderSnapshot.isEmpty) return@withContext Result.failure(IllegalStateException("Sender not found"))
                val senderDoc = senderSnapshot.documents.first()
                val senderBalance = senderDoc.getDouble("balance") ?: 0.0
                if (senderBalance < amount) return@withContext Result.failure(IllegalStateException("Sender has insufficient balance"))

                firestore.collection("wallets")
                    .document(senderDoc.id)
                    .update("balance", senderBalance - amount)
                    .await()
                walletDao.insertWallet(
                    LocalWallet(
                        id = senderDoc.getString("user_id") ?: userId,
                        userId = userId,
                        email = senderEmail,
                        balance = senderBalance - amount,
                        createdAt = senderDoc.getString("created_at") ?: OffsetDateTime.now().toString(),
                        updatedAt = OffsetDateTime.now().toString()
                    )
                )

                val receiverSnapshot = firestore.collection("wallets")
                    .whereEqualTo("email", receiverEmail)
                    .get()
                    .await()
                if (!receiverSnapshot.isEmpty) {
                    val receiverDoc = receiverSnapshot.documents.first()
                    val receiverBalance = receiverDoc.getDouble("balance") ?: 0.0
                    firestore.collection("wallets")
                        .document(receiverDoc.id)
                        .update("balance", receiverBalance + amount)
                        .await()
                    walletDao.insertWallet(
                        LocalWallet(
                            id = receiverDoc.getString("user_id") ?: userId,
                            userId = userId,
                            email = receiverEmail,
                            balance = receiverBalance + amount,
                            createdAt = receiverDoc.getString("created_at") ?: OffsetDateTime.now().toString(),
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                } else {
                    val receiverUserSnapshot = firestore.collection("users")
                        .whereEqualTo("email", receiverEmail)
                        .get()
                        .await()
                    if (receiverUserSnapshot.isEmpty) return@withContext Result.failure(IllegalStateException("Receiver not found"))
                    val receiverUserId = receiverUserSnapshot.documents.first().getString("user_id") ?: ""
                    val newWallet = mapOf(
                        "user_id" to receiverUserId,
                        "email" to receiverEmail,
                        "balance" to amount,
                        "created_at" to OffsetDateTime.now().toString(),
                        "updated_at" to OffsetDateTime.now().toString()
                    )
                    firestore.collection("wallets").document(receiverUserId).set(newWallet).await()
                    walletDao.insertWallet(
                        LocalWallet(
                            id = receiverUserId,
                            userId = receiverUserId,
                            email = receiverEmail,
                            balance = amount,
                            createdAt = OffsetDateTime.now().toString(),
                            updatedAt = OffsetDateTime.now().toString()
                        )
                    )
                }

                firestore.collection("transactions").document(transactionId).set(
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
                ).await()
                val senderWalletDoc = firestore.collection("wallets")
                    .whereEqualTo("email", senderEmail)
                    .get()
                    .await()
                val senderUserIdOnline = senderWalletDoc.documents.firstOrNull()?.getString("user_id")
                    ?: firestore.collection("users").whereEqualTo("email", senderEmail).get().await().documents.firstOrNull()?.getString("user_id")
                    ?: "offline_${senderEmail.hashCode()}"
                firestore.collection("transactions").document(UUID.randomUUID().toString()).set(
                    mapOf(
                        "id" to UUID.randomUUID().toString(),
                        "user_id" to senderUserIdOnline,
                        "sender_email" to senderEmail,
                        "receiver_email" to receiverEmail,
                        "amount" to amount,
                        "type" to "send",
                        "timestamp" to timestamp,
                        "status" to "completed"
                    )
                ).await()
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
                        userId = senderUserIdOnline,
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
                val senderWallet = walletDao.getWalletByEmail(senderEmail)
                    ?: return@withContext Result.failure(IllegalStateException("Sender wallet not found"))
                if (senderWallet.balance < amount) return@withContext Result.failure(IllegalStateException("Insufficient balance"))
                walletDao.insertWallet(
                    senderWallet.copy(balance = senderWallet.balance - amount, updatedAt = OffsetDateTime.now().toString())
                )

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
            Log.e("WalletRepository", "Error receiving money: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getTransactions(userId: String): Result<List<WalletTransactions>> = withContext(Dispatchers.IO) {
        try {
            if (isOnline()) {

                val response = firestore.collection("transactions")
                    .whereEqualTo("user_id", userId)
                    .get()
                    .await()

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


}
