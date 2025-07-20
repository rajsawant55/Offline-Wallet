package com.hackathon.offlinewallet.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity
@Serializable
data class LocalUser(
    @PrimaryKey val id: String,
    val email: String,
    val username: String,
    val createdAt: String,
    val needsSync: Boolean = false,
    val passwordHash: String
)