package com.fillercoach.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val videoPath: String,
    val transcript: String,
    val durationSeconds: Int,
    val fillerCount: Int,
    val fillerWords: String,       // JSON: {"um":5,"uh":3,...}
    val wordCount: Int,
    val wpm: Int,
    val cleanStreakSeconds: Int,   // longest run without a filler
    val pauseCount: Int,
    val notes: String = ""
)
