package com.hackathon.offlinewallet.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hackathon.offlinewallet.data.AppDatabase
import com.hackathon.offlinewallet.data.WalletRepository
import com.hackathon.offlinewallet.data.AuthRepository
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "wallet_db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .build()
    }

    @Provides
    @Singleton
    fun provideWalletRepository(db: AppDatabase, @ApplicationContext context: Context): WalletRepository {
        return WalletRepository(db.walletDao(), db.transactionDao(), context)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(db: AppDatabase, @ApplicationContext context: Context): AuthRepository {
        return AuthRepository(db.userDao(), db.walletDao(), context, FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
    }
}