package com.hackathon.offlinewallet.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(entities = [LocalUser::class, LocalWallet::class, PendingWalletUpdate::class, WalletTransactions::class], version = 3)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun walletDao(): WalletDao
    abstract fun pendingWalletUpdateDao(): PendingWalletUpdateDao
    abstract fun walletTransactionDao(): WalletTransactionDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE PendingWalletUpdate
                    ADD COLUMN transactionType TEXT NOT NULL DEFAULT 'add';
                """)
                database.execSQL("""
                    ALTER TABLE PendingWalletUpdate
                    ADD COLUMN relatedEmail TEXT;
                """)
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE WalletTransactions (
                        id TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        senderEmail TEXT NOT NULL,
                        receiverEmail TEXT NOT NULL,
                        amount REAL NOT NULL,
                        type TEXT NOT NULL,
                        timestamp TEXT NOT NULL,
                        status TEXT NOT NULL,
                        PRIMARY KEY (id)
                    )
                """)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE LocalUser ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE LocalUser ADD COLUMN passwordHash TEXT")
                database.execSQL("ALTER TABLE LocalWallet ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

@androidx.room.TypeConverters
object Converters {
    @androidx.room.TypeConverter
    fun fromUUID(uuid: UUID?): String? = uuid?.toString()

    @androidx.room.TypeConverter
    fun toUUID(uuidString: String?): UUID? = uuidString?.let { UUID.fromString(it) }
}

