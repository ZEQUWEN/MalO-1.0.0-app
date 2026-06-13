package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val fileType: String? = null, // "image", "video", "pdf"
    val fileName: String? = null,
    val reaction: String? = null
)
