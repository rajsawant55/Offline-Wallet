package com.hackathon.offlinewallet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WalletDao {
    @Query("SELECT * FROM LocalWallet WHERE email = :email")
    suspend fun getWalletByEmail(email: String): LocalWallet?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: LocalWallet)

    @Query("DELETE FROM LocalWallet WHERE email = :email")
    suspend fun deleteWallet(email: String)
}