package com.hackathon.offlinewallet.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WalletRepository,
    private val firestore: FirebaseFirestore
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val unsynced = repository.getUnsyncedTransactions().firstOrNull() ?: emptyList()
            for (transaction in unsynced) {
                firestore.collection("transactions")
                    .add(
                        hashMapOf(
                            "amount" to transaction.amount,
                            "recipient" to transaction.recipient,
                            "type" to transaction.type,
                            "timestamp" to transaction.timestamp
                        )
                    )
                    .await()
                repository.getTransactionDao().updateTransaction(transaction.copy(isSynced = true))
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}