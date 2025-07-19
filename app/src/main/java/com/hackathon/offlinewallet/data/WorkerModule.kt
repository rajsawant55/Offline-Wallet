package com.hackathon.offlinewallet.data

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dagger.Module
import dagger.Provides
import dagger.assisted.AssistedFactory
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {
    @Provides
    @Singleton
    fun provideWorkManagerConfiguration(
        @ApplicationContext context: Context,
        workerFactory: HiltWorkerFactory
    ): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}

class SyncWorkerFactory @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
    private val pendingWalletUpdateDao: PendingWalletUpdateDao,
    private val userDao: UserDao,
    private val walletDao: WalletDao,
    private val walletTransactionDao: WalletTransactionDao

) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name -> SyncWorker(
                context = appContext,
                params = workerParameters,
                supabaseClientProvider = supabaseClientProvider,
                pendingWalletUpdateDao = pendingWalletUpdateDao,
                userDao = userDao,
                walletDao = walletDao,
                walletTransactionDao = walletTransactionDao
            )
            else -> null
        }
    }

}