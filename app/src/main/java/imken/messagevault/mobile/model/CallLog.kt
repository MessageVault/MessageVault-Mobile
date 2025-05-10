package imken.messagevault.mobile.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import android.content.ContentValues
import android.provider.CallLog.Calls

/**
 * 通话记录模型
 *
 * 表示一条通话记录，包含ID、电话号码、类型、时间、持续时间和联系人名称等信息
 * 优化的JSON序列化，使用@Expose和简短的@SerializedName值减小文件大小
 */
data class CallLog(
    /**
     * 通话记录ID
     */
    @Expose
    @SerializedName("id")
    val id: Long,
    
    /**
     * 电话号码
     */
    @Expose
    @SerializedName("num")
    val number: String,
    
    /**
     * 通话类型
     * 1: 来电未接
     * 2: 去电
     * 3: 来电已接
     * 4: 语音信箱
     * 5: 拒接
     * 6: 屏蔽的来电
     */
    @Expose
    @SerializedName("type")
    val type: Int,
    
    /**
     * 通话时间戳（Unix毫秒）
     */
    @Expose
    @SerializedName("date")
    val date: Long,
    
    /**
     * 通话持续时间（秒）
     */
    @Expose
    @SerializedName("dur")
    val duration: Int,
    
    /**
     * 联系人名称（可选）
     * 与电话号码关联的联系人姓名
     */
    @Expose
    @SerializedName("name")
    val contact: String? = null
) {
    /**
     * 转换为ContentValues以便写入系统通话记录数据库
     */
    fun toContentValues(): ContentValues {
        return ContentValues().apply {
            // 注意：不设置ID，让系统自动生成ID
            put(Calls.NUMBER, number)
            put(Calls.TYPE, type)
            put(Calls.DATE, date)
            put(Calls.DURATION, duration)
            contact?.let { put(Calls.CACHED_NAME, it) }
            // 设置新通话记录为已读
            put(Calls.NEW, 0)
            // 标记为非语音邮件
            put(Calls.IS_READ, 1)
        }
    }
}
