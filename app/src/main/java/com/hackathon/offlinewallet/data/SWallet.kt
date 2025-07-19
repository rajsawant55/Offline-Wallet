package com.hackathon.offlinewallet.data

import kotlinx.serialization.Serializable

@Serializable
data class SWallet(
    val user_id: String,
    val email: String,
    val balance: Double,
    val created_at: String?,
    val updated_at: String?
)