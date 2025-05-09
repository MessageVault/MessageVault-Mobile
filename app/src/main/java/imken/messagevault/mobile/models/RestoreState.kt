package imken.messagevault.mobile.models

sealed class RestoreState {
    object Idle : RestoreState()
    object Preparing : RestoreState()
    data class InProgress(val progress: Int, val message: String) : RestoreState()
    data class Completed(val success: Boolean, val message: String) : RestoreState()
    data class Error(val message: String) : RestoreState()
}
