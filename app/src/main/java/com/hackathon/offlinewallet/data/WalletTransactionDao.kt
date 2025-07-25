package com.hackathon.offlinewallet.data

@androidx.room.Dao
interface WalletTransactionDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: WalletTransactions)

    @androidx.room.Query("SELECT * FROM WalletTransactions WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getTransactionsByUserId(userId: String): List<WalletTransactions>

    @androidx.room.Query("SELECT * FROM WalletTransactions WHERE userId = :userId AND status = :status ORDER BY timestamp DESC")
    suspend fun getPendingTransactionsByUserId(userId: String, status: String = "pending"): List<WalletTransactions>

    @androidx.room.Query("SELECT * FROM WalletTransactions WHERE status = :status ORDER BY timestamp DESC")
    suspend fun getPendingTransactions(status: String = "pending"): List<WalletTransactions>

    @androidx.room.Query("DELETE FROM WalletTransactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)


}