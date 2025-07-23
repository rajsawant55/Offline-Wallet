package com.hackathon.offlinewallet.di

import android.content.Context
import androidx.room.Room
import com.hackathon.offlinewallet.data.AppDatabase
import com.hackathon.offlinewallet.data.AuthRepository
import com.hackathon.offlinewallet.data.BluetoothService
import com.hackathon.offlinewallet.data.PendingWalletUpdateDao
import com.hackathon.offlinewallet.data.WalletTransactionDao
import com.hackathon.offlinewallet.data.SupabaseClientProvider
import com.hackathon.offlinewallet.data.WalletDao
import com.hackathon.offlinewallet.data.WalletRepository
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
    fun provideSupabaseClientProvider(): SupabaseClientProvider {
        println("Supabase callded")
        return SupabaseClientProvider()

    }

    @Provides
    @Singleton
    fun provideAuthRepository(

        supabaseClientProvider: SupabaseClientProvider,
        userDao: com.hackathon.offlinewallet.data.UserDao,
        walletDao: WalletDao,
        @ApplicationContext context: Context
    ): AuthRepository {
        println("Auth repo called")

        return AuthRepository(supabaseClientProvider, userDao, context, walletDao)
    }

    @Provides
    @Singleton
    fun provideWalletRepository(
        supabaseClientProvider: SupabaseClientProvider,
        walletDao: WalletDao,
        pendingWalletUpdateDao: PendingWalletUpdateDao,
        walletTransactionDao: WalletTransactionDao,
        @ApplicationContext context: Context

    ): WalletRepository {
        println("wallet repo called")

        return WalletRepository(supabaseClientProvider, walletDao, pendingWalletUpdateDao, walletTransactionDao, context)
    }

    @Provides
    @Singleton
    fun provideBluetoothService(
        @ApplicationContext context: Context,
        walletRepository: WalletRepository
    ): BluetoothService {
        return BluetoothService(context, walletRepository)
    }
}