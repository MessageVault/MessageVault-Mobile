package imken.messagevault.mobile.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import imken.messagevault.mobile.model.Contact

/**
 * 备份数据模型
 */
data class BackupData(
    @Expose
    @SerializedName("messages")
    val messages: List<Message>? = emptyList(),
    
    @Expose
    @SerializedName("call_logs")
    val callLogs: List<CallLog>? = emptyList(),
    
    @Expose
    @SerializedName("contacts")
    val contacts: List<Contact>? = emptyList(),
    
    @Expose
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @Expose
    @SerializedName("device_info")
    val deviceInfo: String = ""
)
