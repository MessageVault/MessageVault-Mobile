package imken.messagevault.mobile.data.models

import java.io.File

/**
 * 表示一个备份文件
 */
data class BackupFile(
    val id: String,
    val deviceId: String,
    val fileName: String,
    val timestamp: Long,
    val appVersion: String,
    val size: Long,
    val localFile: File? = null
    // 其他必要属性
)
