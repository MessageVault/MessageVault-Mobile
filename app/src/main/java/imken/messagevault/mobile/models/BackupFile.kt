package imken.messagevault.mobile.models

import java.util.Date

data class BackupFile(
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val creationDate: Date,
    val deviceName: String,
    val smsCount: Int,
    val callLogsCount: Int,
    val version: String,
    val timestamp: Long = creationDate.time,
    val deviceId: String = deviceName
)
