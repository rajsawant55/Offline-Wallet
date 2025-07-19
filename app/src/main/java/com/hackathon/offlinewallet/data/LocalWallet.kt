package com.hackathon.offlinewallet.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity
@Serializable
data class LocalWallet(
    @PrimaryKey val id: String,
    val userId: String,
    val email: String,
    val balance: Double,
    val createdAt: String,
    val updatedAt: String,
    val needsSync: Boolean = false
)