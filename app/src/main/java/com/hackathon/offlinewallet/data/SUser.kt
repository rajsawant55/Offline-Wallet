package com.hackathon.offlinewallet.data

import kotlinx.serialization.Serializable

@Serializable
data class SUser(
    val user_id: String,
    val user_name: String,
    val email: String,
    val created_at: String,
    val fb_id: String?
)