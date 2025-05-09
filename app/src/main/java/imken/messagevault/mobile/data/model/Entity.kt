package imken.messagevault.mobile.data.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.File

/**
 * 备份信息实体类
 */
data class BackupInfo(
    val id: Long = 0,
    val timestamp: Long,          // 备份时间戳，确保此字段存在
    val fileName: String,
    val smsCount: Int = 0,        // 短信数量，确保此字段存在
    val callLogsCount: Int = 0,   // 通话记录数量，确保此字段存在
    val backupPath: String
)

/**
 * 备份文件实体类
 */
/*
data class BackupFile(
    val file: File,
    val name: String = file.name,
    val timestamp: Long = file.lastModified(),
    val deviceName: String = "Android设备",
    val smsCount: Int? = null,
    val callLogsCount: Int? = null,
    val fileSize: Long = file.length(),
    val lastModified: Long = file.lastModified()
)
*/

/**
 * 应用备份状态
 */
data class EmptyEntity(val id: String = "") // 添加至少一个参数

