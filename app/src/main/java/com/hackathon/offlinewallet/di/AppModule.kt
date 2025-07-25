package com.hackathon.offlinewallet.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hackathon.offlinewallet.data.AppDatabase
import com.hackathon.offlinewallet.data.AuthRepository
import com.hackathon.offlinewallet.data.BluetoothService
import com.hackathon.offlinewallet.data.PendingWalletUpdateDao
import com.hackathon.offlinewallet.data.WalletTransactionDao
import com.hackathon.offlinewallet.data.WalletDao
import com.hackathon.offlinewallet.data.WalletRepository
import com.hackathon.offlinewallet.ui.AuthViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        println("app database called")
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "offline-wallet-db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
    }

    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): com.hackathon.offlinewallet.data.UserDao = database.userDao()

    @Provides
    @Singleton
    fun provideWalletDao(database: AppDatabase): com.hackathon.offlinewallet.data.WalletDao = database.walletDao()

    @Provides
    @Singleton
    fun providePendingWalletUpdateDao(database: AppDatabase): com.hackathon.offlinewallet.data.PendingWalletUpdateDao = database.pendingWalletUpdateDao()

    @Provides
    @Singleton
    fun provideWalletTransactionDao(database: AppDatabase): com.hackathon.offlinewallet.data.WalletTransactionDao = database.walletTransactionDao()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        firebaseAuth: FirebaseAuth,
        firestore: FirebaseFirestore,
        userDao: com.hackathon.offlinewallet.data.UserDao,
        walletDao: WalletDao,
        @ApplicationContext context: Context
    ): AuthRepository {
        println("Auth repo called")
        return AuthRepository(firebaseAuth, firestore, userDao, context, walletDao)
    }

    @Provides
    @Singleton
    fun provideWalletRepository(
        firebaseAuth: FirebaseAuth,
        firestore: FirebaseFirestore,
        walletDao: WalletDao,
        pendingWalletUpdateDao: PendingWalletUpdateDao,
        walletTransactionDao: WalletTransactionDao,
        @ApplicationContext context: Context
    ): WalletRepository {
        println("wallet repo called")
        return WalletRepository(firebaseAuth, firestore, walletDao, pendingWalletUpdateDao, walletTransactionDao, context)
    }

    @Provides
    @Singleton
    fun provideBluetoothService(
        @ApplicationContext context: Context,
        walletRepository: WalletRepository,
        authRepository: AuthRepository
    ): BluetoothService {
        return BluetoothService(context, walletRepository, authRepository)
    }
}