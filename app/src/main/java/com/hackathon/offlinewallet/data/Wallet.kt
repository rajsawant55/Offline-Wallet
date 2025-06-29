package com.hackathon.offlinewallet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallet")
data class Wallet(
    @PrimaryKey val id: String = "user_wallet",
    val balance: Double
)