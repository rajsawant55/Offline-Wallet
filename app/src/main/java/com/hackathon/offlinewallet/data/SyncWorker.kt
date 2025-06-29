package com.hackathon.offlinewallet.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WalletRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (repository.isOnline()) {
            val unsynced = repository.transactionDao.getUnsyncedTransactions()
            unsynced.forEach { transaction ->
                // Simulate API call to sync transaction
                repository.transactionDao.markAsSynced(transaction.id)
            }
            Result.success()
        } else {
            Result.retry()
        }
    }
}