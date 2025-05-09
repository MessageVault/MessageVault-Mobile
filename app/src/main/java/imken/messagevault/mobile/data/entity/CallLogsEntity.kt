package imken.messagevault.mobile.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 通话记录实体类
 * 
 * 用于将通话记录数据存储在本地数据库中
 */
@Entity(tableName = "call_logs")
data class CallLogsEntity(
    /**
     * 主键ID
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * 电话号码
     */
    val number: String,
    
    /**
     * 联系人名称
     */
    val name: String,
    
    /**
     * 通话时间戳
     */
    val date: Long,
    
    /**
     * 通话持续时间（秒）
     */
    val duration: Int,
    
    /**
     * 通话类型
     * 1: 来电未接
     * 2: 去电
     * 3: 来电已接
     * 4: 语音信箱
     * 5: 拒接
     * 6: 屏蔽的来电
     */
    val type: Int
)
