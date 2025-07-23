package com.hackathon.offlinewallet // Or your app's package name

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hackathon.offlinewallet.data.PendingWalletUpdateDao
import com.hackathon.offlinewallet.data.SyncWorker
import com.hackathon.offlinewallet.data.UserDao
import com.hackathon.offlinewallet.data.WalletDao
import com.hackathon.offlinewallet.data.WalletTransactionDao
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OfflineWalletApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: SyncWorkerFactory


    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG)
            .setWorkerFactory(workerFactory).build()
}

class SyncWorkerFactory @Inject constructor(
    private val pendingWalletUpdateDao: PendingWalletUpdateDao,
    private val userDao: UserDao,
    private val walletDao: WalletDao,
    private val walletTransactionDao: WalletTransactionDao,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        println("sync worker called")
        return when (workerClassName) {
            SyncWorker::class.java.name -> SyncWorker(
                context = appContext,
                params = workerParameters,
                pendingWalletUpdateDao = pendingWalletUpdateDao,
                userDao = userDao,
                walletDao = walletDao,
                walletTransactionDao = walletTransactionDao,
                firebaseAuth = firebaseAuth,
                firestore = firestore
            )
            else -> null
        }
    }

}