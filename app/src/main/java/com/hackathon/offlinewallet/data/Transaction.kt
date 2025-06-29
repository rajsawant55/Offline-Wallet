package com.hackathon.offlinewallet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val recipient: String,
	val type: String, // ADD, SEND, MERCHANT, UPI
    val timestamp: Long,
    val isSynced: Boolean = false
)