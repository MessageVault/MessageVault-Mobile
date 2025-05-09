package imken.messagevault.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import imken.messagevault.mobile.data.entity.CallLogsEntity

/**
 * 通话记录DAO接口
 */
@Dao
interface CallLogDao {
    
    /**
     * 插入通话记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLogs(callLogs: List<CallLogsEntity>)
    
    /**
     * 获取所有通话记录，按日期倒序排列
     */
    @Query("SELECT * FROM call_logs ORDER BY date DESC")
    suspend fun getAllCallLogs(): List<CallLogsEntity>
    
    /**
     * 删除所有通话记录
     */
    @Query("DELETE FROM call_logs")
    suspend fun deleteAllCallLogs()
    
    /**
     * 获取通话记录数量
     */
    @Query("SELECT COUNT(id) FROM call_logs")
    suspend fun getCallLogsCount(): Int
} 