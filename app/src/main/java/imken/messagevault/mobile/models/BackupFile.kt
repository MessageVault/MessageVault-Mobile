package imken.messagevault.mobile.models

import java.util.Date

data class BackupFile(
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val creationDate: Date,
    val deviceName: String,
    val smsCount: Int = 0,
    val callLogsCount: Int = 0,
    val version: String = "",
    val timestamp: Long = creationDate.time,
    val deviceId: String = deviceName,
    val lastModified: Long = timestamp,
    val dateTimeStr: String = creationDate.toString(),
    val deviceInfo: String = deviceName
)
