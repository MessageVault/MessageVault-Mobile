package imken.messagevault.mobile.data.models

import imken.messagevault.mobile.data.BackupFile
import java.io.File
import java.util.Date

/**
 * 备份文件信息模型
 */
data class BackupFileInfo(
    val id: String,
    val name: String,
    val path: String,
    val deviceName: String,
    val date: Date,
    val smsCount: Int,
    val callLogsCount: Int,
    val fileSize: Long,
    val file: File? = null
) {
    companion object {
        // 转换方法，用于将 BackupFile 转换为 BackupFileInfo
        fun fromBackupFile(backupFile: BackupFile): BackupFileInfo {
            return BackupFileInfo(
                id = backupFile.id,
                name = backupFile.fileName,
                path = backupFile.localFile?.absolutePath ?: "",
                deviceName = backupFile.deviceId,
                date = Date(backupFile.timestamp),
                smsCount = 0, // 这些信息在BackupFile中不存在，设置默认值
                callLogsCount = 0,
                fileSize = backupFile.size,
                file = backupFile.localFile
            )
        }
    }
}
