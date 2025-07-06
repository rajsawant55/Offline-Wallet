package com.hackathon.offlinewallet.data

import androidx.room.Entity
import kotlinx.serialization.Serializable

@Entity
@Serializable
data class LocalWallet(
    @androidx.room.PrimaryKey val id: String,
    val userId: String,
    val email: String,
    val balance: Double,
    val createdAt: String,
    val updatedAt: String
)