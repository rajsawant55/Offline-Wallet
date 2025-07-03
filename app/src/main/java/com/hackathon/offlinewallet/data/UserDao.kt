package com.hackathon.offlinewallet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert
    suspend fun insertUser(user: User)

    @Query("UPDATE users SET jwtToken = :jwtToken WHERE email = :email")
    suspend fun updateJwtToken(email: String, jwtToken: String?)

    @Query("SELECT * FROM users WHERE email = :email")
    fun getUser(email: String): Flow<User?>

    @Query("SELECT * FROM users WHERE username = :username")
    fun getUserByUsername(username: String): Flow<User?>
}