package com.hackathon.offlinewallet.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hackathon.offlinewallet.data.AppDatabase
import com.hackathon.offlinewallet.data.Wallet
import com.hackathon.offlinewallet.data.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "wallet_db")
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Initialize default wallet only if it doesn't exist
                    runBlocking {
                        val database = Room.databaseBuilder(context, AppDatabase::class.java, "wallet_db").build()
                        val walletDao = database.walletDao()
                        val existingWallet = walletDao.getWallet("user_wallet").firstOrNull()
                        if (existingWallet == null) {
                            walletDao.insertWallet(Wallet(id = "user_wallet", balance = 0.0))
                        }
                        database.close()
                    }
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideWalletRepository(db: AppDatabase, @ApplicationContext context: Context): WalletRepository {
        return WalletRepository(db.walletDao(), db.transactionDao(), context)
    }
}