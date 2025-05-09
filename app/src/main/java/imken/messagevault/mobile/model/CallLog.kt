package imken.messagevault.mobile.model

import com.google.gson.annotations.SerializedName

/**
 * 通话记录模型
 *
 * 表示一条通话记录，包含ID、电话号码、类型、时间、持续时间和联系人名称等信息
 */
data class CallLog(
    /**
     * 通话记录ID
     */
    @SerializedName("id")
    val id: Long,
    
    /**
     * 电话号码
     */
    @SerializedName("number")
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
    @SerializedName("type")
    val type: Int,
    
    /**
     * 通话时间戳（Unix毫秒）
     */
    @SerializedName("date")
    val date: Long,
    
    /**
     * 通话持续时间（秒）
     */
    @SerializedName("duration")
    val duration: Int,
    
    /**
     * 联系人名称（可选）
     * 与电话号码关联的联系人姓名
     */
    @SerializedName("contact")
    val contact: String? = null
)
