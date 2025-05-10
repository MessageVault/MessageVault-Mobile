package imken.messagevault.mobile.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import android.content.ContentValues
import android.provider.Telephony

/**
 * 短信消息模型类
 * 
 * 优化的JSON序列化，使用@Expose和简短的@SerializedName值减小文件大小
 */
data class Message(
    /**
     * 短信ID
     */
    @Expose
    @SerializedName("id")
    val id: Long = 0,
    
    /**
     * 短信地址（发送人/接收人号码）
     */
    @Expose
    @SerializedName("addr")
    val address: String,
    
    /**
     * 短信内容
     */
    @Expose
    @SerializedName("body")
    val body: String? = "",
    
    /**
     * 短信时间戳（毫秒）
     */
    @Expose
    @SerializedName("date")
    val date: Long,
    
    /**
     * 短信类型
     * 1: 收件箱, 2: 发送, 3: 草稿, 4: 发件箱, 5: 失败, 6: 排队
     */
    @Expose
    @SerializedName("type")
    val type: Int,
    
    /**
     * 已读状态 (0: 未读, 1: 已读)
     * 不需要在JSON中，因此没有@Expose注解
     */
    @SerializedName("read")
    val readState: Int? = 0,
    
    /**
     * 短信状态
     * 不需要在JSON中，因此没有@Expose注解
     */
    @SerializedName("status")
    val messageStatus: Int? = 0,
    
    /**
     * 对话ID
     * 不需要在JSON中，因此没有@Expose注解
     */
    @SerializedName("thread_id")
    val threadId: Long = 0
) {
    /**
     * 转换为ContentValues以便写入系统短信数据库
     */
    fun toContentValues(): ContentValues {
        return ContentValues().apply {
            // 不设置ID，让系统自动生成
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body ?: "")
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.TYPE, type)
            readState?.let { put(Telephony.Sms.READ, it) }
            messageStatus?.let { put(Telephony.Sms.STATUS, it) }
            if (threadId > 0) {
                put(Telephony.Sms.THREAD_ID, threadId)
            }
        }
    }
}
