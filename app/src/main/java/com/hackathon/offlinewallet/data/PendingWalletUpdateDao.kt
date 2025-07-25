package com.hackathon.offlinewallet.data

import androidx.room.Query

@androidx.room.Dao
interface PendingWalletUpdateDao {
    @androidx.room.Insert
    suspend fun insertPendingUpdate(update: PendingWalletUpdate)

    @androidx.room.Query("SELECT * FROM PendingWalletUpdate")
    suspend fun getAllPendingUpdates(): List<PendingWalletUpdate>

    @androidx.room.Query("DELETE FROM PendingWalletUpdate WHERE updateId = :updateId")
    suspend fun deletePendingUpdate(updateId: Long)

    @Query("DELETE FROM PendingWalletUpdate WHERE timestamp = :timestamp")
    suspend fun deletePendingUpdatesByTimestamp(timestamp: String)
}