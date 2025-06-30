package com.hackathon.offlinewallet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val email: String,
    val username: String,
    val passwordHash: String, // Hashed password
    val jwtToken: String? = null // Stored JWT for offline use
)