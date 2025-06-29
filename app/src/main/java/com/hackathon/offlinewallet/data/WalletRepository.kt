package com.hackathon.offlinewallet.data

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class WalletRepository @Inject constructor(
    private val walletDao: WalletDao,
     val transactionDao: TransactionDao,
    private val context: Context
) {
    fun getWallet(): Flow<Wallet?> = walletDao.getWallet("user_wallet")

    suspend fun addMoney(amount: Double) {
        if (amount <= 0) return
        val wallet = walletDao.getWallet("user_wallet").firstOrNull() ?: Wallet(id = "user_wallet", balance = 0.0)
        walletDao.insertWallet(wallet.copy(balance = wallet.balance + amount))
        transactionDao.insertTransaction(Transaction(amount = amount, recipient = "Self", type = "ADD", timestamp = System.currentTimeMillis()))
        scheduleSync()
    }

    suspend fun sendMoney(amount: Double, recipient: String, type: String = "SEND", isUpi: Boolean = false): Boolean {
        if (amount <= 0 || recipient.isBlank()) return false
        val wallet = walletDao.getWallet("user_wallet").firstOrNull() ?: return false
        if (wallet.balance < amount) return false
        if (isUpi && !isOnline()) return false // UPI requires online connection

        walletDao.updateWallet(wallet.copy(balance = wallet.balance - amount))
        transactionDao.insertTransaction(Transaction(
            amount = amount,
            recipient = recipient,
            type = if (isUpi) "UPI" else type,
            timestamp = System.currentTimeMillis(),
            isSynced = !isUpi // QR transactions are offline, UPI are synced immediately if online
        ))
        scheduleSync()
        return true
    }

    fun getTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        return network != null
    }

    private fun scheduleSync() {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(syncRequest)
    }
}