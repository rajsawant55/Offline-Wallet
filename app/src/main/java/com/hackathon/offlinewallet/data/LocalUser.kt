package com.hackathon.offlinewallet.data

import androidx.room.Entity
import kotlinx.serialization.Serializable

@Entity
@Serializable
data class LocalUser(
    @androidx.room.PrimaryKey val id: String,
    val email: String,
    val username: String,
    val createdAt: String
)