package imken.messagevault.mobile.models

sealed class BackupState {
    object Initial : BackupState()
    object Preparing : BackupState()
    data class Completed(val filePath: String) : BackupState()
    object Error : BackupState()
    data class InProgress(
        val progress: Int = 0,
        val message: String = ""
    ) : BackupState()
}
