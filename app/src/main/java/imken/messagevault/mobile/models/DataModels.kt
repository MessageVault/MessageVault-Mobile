package imken.messagevault.mobile.models

import java.io.File
import java.util.Date

data class MessageDataOld(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int // 1: received, 2: sent
)

data class CallLogDataOld(
    val id: Long,
    val number: String,
    val name: String?,
    val date: Long,
    val duration: Int,
    val type: Int // 1: incoming, 2: outgoing, 3: missed
)

data class ContactDataOld(
    val id: Long,
    val name: String,
    val phoneNumbers: List<String>
)

data class BackupFileInfoOld(
    val file: File,
    val name: String,
    val date: Date,
    val formatVersion: Int = 1
)

sealed class BackupStateOld {
    object Idle : BackupStateOld()
    object Preparing : BackupStateOld()
    object InProgress : BackupStateOld()
    data class Progress(val progress: Int, val message: String) : BackupStateOld()
    object Completed : BackupStateOld()
    data class Error(val message: String) : BackupStateOld()
}
