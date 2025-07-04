package com.hackathon.offlinewallet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @androidx.room.Query("SELECT * FROM LocalUser WHERE email = :email")
    suspend fun getUserByEmail(email: String): LocalUser?

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: LocalUser)

    @androidx.room.Query("DELETE FROM LocalUser WHERE email = :email")
    suspend fun deleteUser(email: String)

    @androidx.room.Query("SELECT * FROM LocalUser")
    suspend fun getAllUsers(): List<LocalUser>
}