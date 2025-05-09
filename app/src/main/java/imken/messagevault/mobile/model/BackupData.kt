package imken.messagevault.mobile.model

import imken.messagevault.mobile.model.Contact

/**
 * 备份数据模型
 */
data class BackupData(
    val messages: List<Message>? = emptyList(),
    val callLogs: List<CallLog>? = emptyList(),
    val contacts: List<Contact>? = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val deviceInfo: String = ""
)
