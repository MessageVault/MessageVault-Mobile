// ...existing code...

sealed class RestoreState {
    object Idle : RestoreState()
    data class InProgress(val operation: String, val progress: Int, val message: String = "") : RestoreState()
    data class Success(val message: String = "") : RestoreState()
    data class Error(val message: String) : RestoreState()
}

// ...existing code...
