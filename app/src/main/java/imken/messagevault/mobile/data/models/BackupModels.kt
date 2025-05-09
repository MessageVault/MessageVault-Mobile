package imken.messagevault.mobile.data.models

import java.io.File

// 备份数据模型
data class BackupData(
    val formatVersion: Int = SUPPORTED_VERSION,
    val messages: List<MessageData> = emptyList(),
    val callLogs: List<CallLogData> = emptyList(),
    val contacts: List<ContactData> = emptyList()
)

// 消息数据模型
data class MessageData(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean? = false,
    val status: Int = 0
)

// 通话记录数据模型
data class CallLogData(
    val id: Long,
    val number: String,
    val name: String?,
    val date: Long,
    val duration: Int,
    val type: Int
)

// 联系人数据模型
data class ContactData(
    val id: Long,
    val name: String,
    val phoneNumbers: List<String>,
    val emails: List<String>? = null
)

// 备份状态枚举
sealed class BackupState {
    object Idle : BackupState()
    object Preparing : BackupState()
    data class InProgress(val progress: Int) : BackupState()
    object Completed : BackupState()
    data class Error(val message: String) : BackupState()
}

// 恢复状态枚举
sealed class RestoreState {
    object Idle : RestoreState()
    object Preparing : RestoreState()
    data class InProgress(val progress: Int) : RestoreState()
    object Completed : RestoreState()
    data class Error(val message: String) : RestoreState()
}

// 支持的备份格式版本
const val SUPPORTED_VERSION = 1
