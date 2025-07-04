package com.hackathon.offlinewallet.data

import androidx.room.Entity

@Entity
data class PendingWalletUpdate(
    @androidx.room.PrimaryKey(autoGenerate = true) val updateId: Long = 0,
    val email: String,
    val amount: Double,
    val timestamp: String,
    val transactionType: String, // "add", "send", "receive"
    val relatedEmail: String? // For send/receive: other party's email
)