package imken.messagevault.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import imken.messagevault.mobile.data.entity.MessageEntity

/**
 * 短信消息DAO接口
 */
@Dao
interface MessageDao {
    
    /**
     * 插入短信消息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    /**
     * 获取所有短信消息，按日期倒序排列
     */
    @Query("SELECT * FROM messages ORDER BY date DESC")
    suspend fun getAllMessages(): List<MessageEntity>
    
    /**
     * 删除所有短信消息
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
    
    /**
     * 获取短信消息数量
     */
    @Query("SELECT COUNT(id) FROM messages")
    suspend fun getMessagesCount(): Int
} 