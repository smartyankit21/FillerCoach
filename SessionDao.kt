package com.fillercoach.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: Session): Long

    @Delete
    suspend fun delete(session: Session)

    @Update
    suspend fun update(session: Session)

    @Query("SELECT * FROM sessions ORDER BY timestamp DESC")
    fun getAllSessions(): LiveData<List<Session>>

    @Query("SELECT * FROM sessions ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSession(): Session?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): Session?

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getTotalSessions(): Int

    @Query("SELECT AVG(fillerCount) FROM sessions")
    suspend fun getAvgFillerCount(): Float

    @Query("SELECT AVG(wpm) FROM sessions")
    suspend fun getAvgWpm(): Float

    @Query("SELECT * FROM sessions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<Session>
}
