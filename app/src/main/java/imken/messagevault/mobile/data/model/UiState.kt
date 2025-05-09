package imken.messagevault.mobile.data.model

import androidx.compose.runtime.MutableState

/**
 * 表示UI状态的接口，用于BackupViewModel等
 */
interface UiState<T> : MutableState<T>
