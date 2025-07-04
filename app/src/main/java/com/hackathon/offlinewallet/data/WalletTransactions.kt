package com.hackathon.offlinewallet.data

import androidx.room.Entity

@Entity(tableName = "WalletTransactions")
data class WalletTransactions(
    @androidx.room.PrimaryKey val id: String,
    val userId: String,
    val senderEmail: String,
    val receiverEmail: String,
    val amount: Double,
    val type: String, // "send", "receive"
    val timestamp: String,
    val status: String // "pending", "completed"
)
