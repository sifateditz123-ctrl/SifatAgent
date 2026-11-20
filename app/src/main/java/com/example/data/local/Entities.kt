package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // "USER", "ASSISTANT", "SYSTEM"
    val content: String,
    val language: String = "en",
    val actionPlanJson: String? = null,
    val actionStatus: String? = null, // "PENDING_CONFIRMATION", "EXECUTING", "COMPLETED", "CANCELLED", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userPrompt: String,
    val actionName: String,
    val parametersSummary: String,
    val riskLevel: String,
    val status: String, // "SUCCESS", "CANCELLED", "DENIED", "FAILED"
    val resultMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)
