package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET actionStatus = :status, content = :updatedContent WHERE id = :id")
    suspend fun updateActionState(id: Long, status: String, updatedContent: String)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

@Dao
interface CommandHistoryDao {
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC, id DESC")
    fun observeHistory(): Flow<List<CommandHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: CommandHistoryEntity): Long

    @Query("DELETE FROM command_history WHERE id = :id")
    suspend fun deleteHistoryItem(id: Long)

    @Query("DELETE FROM command_history")
    suspend fun clearAllHistory()
}

@Database(
    entities = [ChatMessageEntity::class, CommandHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SifatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun commandHistoryDao(): CommandHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SifatDatabase? = null

        fun getInstance(context: Context): SifatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SifatDatabase::class.java,
                    "sifat_agent.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class HistoryRepository(
    private val chatDao: ChatDao,
    private val commandHistoryDao: CommandHistoryDao
) {
    val messages: Flow<List<ChatMessageEntity>> = chatDao.observeMessages()
    val commandHistory: Flow<List<CommandHistoryEntity>> = commandHistoryDao.observeHistory()

    suspend fun addMessage(message: ChatMessageEntity): Long = chatDao.insertMessage(message)

    suspend fun updateMessageActionState(id: Long, status: String, updatedContent: String) =
        chatDao.updateActionState(id, status, updatedContent)

    suspend fun deleteMessage(id: Long) = chatDao.deleteMessage(id)

    suspend fun clearChatHistory() = chatDao.clearAllMessages()

    suspend fun logCommand(entry: CommandHistoryEntity): Long = commandHistoryDao.insertHistory(entry)

    suspend fun deleteCommandHistoryItem(id: Long) = commandHistoryDao.deleteHistoryItem(id)

    suspend fun clearCommandHistory() = commandHistoryDao.clearAllHistory()
}
