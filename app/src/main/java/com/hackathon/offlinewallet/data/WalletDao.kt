package com.hackathon.offlinewallet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface WalletDao {
    @Query("SELECT * FROM LocalWallet WHERE email = :email")
    suspend fun getWalletByEmail(email: String): LocalWallet?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: LocalWallet)

    @Query("DELETE FROM LocalWallet WHERE email = :email")
    suspend fun deleteWallet(email: String)

    @Query("SELECT * FROM LocalWallet WHERE userId = :userId")
    suspend fun getWallet(userId: String): LocalWallet?

    @Query("SELECT * FROM LocalWallet WHERE needsSync = 1")
    suspend fun getPendingWallets(): List<LocalWallet>

    @Query("UPDATE LocalWallet SET needsSync = 0, id = :newId, userId = :newUserId WHERE userId = :oldUserId")
    suspend fun markWalletSynced(oldUserId: String, newId: String, newUserId: String)
}