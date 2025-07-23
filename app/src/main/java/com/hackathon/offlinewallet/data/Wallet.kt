package com.hackathon.offlinewallet.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "wallet",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["email"],
        childColumns = ["userEmail"],
        onDelete = ForeignKey.CASCADE
    )]
)
@Serializable
data class Wallet(
    @PrimaryKey val userId: String,
    val balance: Double,
    val email: String
)