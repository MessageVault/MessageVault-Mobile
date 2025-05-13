package imken.messagevault.mobile.ui.restore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import imken.messagevault.mobile.data.RestoreManager
import imken.messagevault.mobile.models.BackupFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 恢复界面的ViewModel
 * 负责管理恢复过程的状态和与RestoreManager的交互
 */
class RestoreViewModel(
    private val restoreManager: RestoreManager
) : ViewModel() {
    
    // 恢复状态流
    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState
    
    // 可用备份文件列表
    private val _availableBackups = MutableStateFlow<List<BackupFile>>(emptyList())
    val availableBackups: StateFlow<List<BackupFile>> = _availableBackups
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    /**
     * 加载可用的备份文件
     */
    fun loadAvailableBackups() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val backups = restoreManager.getAvailableBackups()
                _availableBackups.value = backups
                Timber.i("[Mobile] INFO [RestoreViewModel] 加载了 ${backups.size} 个备份文件")
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [RestoreViewModel] 加载备份文件失败: ${e.message}")
                _restoreState.value = RestoreState.Error("加载备份文件失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 从选定的备份文件恢复数据
     * 
     * @param backupFile 要恢复的备份文件
     */
    fun restoreFromFile(backupFile: BackupFile) {
        viewModelScope.launch {
            try {
                _restoreState.value = RestoreState.InProgress("准备", 0)
                val result = restoreManager.restoreFromFile(backupFile, restoreCallback)
                if (result.success) {
                    _restoreState.value = RestoreState.Success(result.message)
                } else {
                    _restoreState.value = RestoreState.Error(result.message)
                }
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [RestoreViewModel] 恢复过程发生异常: ${e.message}")
                _restoreState.value = RestoreState.Error("恢复失败: ${e.message}")
            }
        }
    }
    
    /**
     * 取消恢复过程
     */
    fun cancelRestore() {
        viewModelScope.launch {
            try {
                // 这里可以添加取消恢复过程的逻辑
                _restoreState.value = RestoreState.Idle
                Timber.i("[Mobile] INFO [RestoreViewModel] 用户取消了恢复过程")
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [RestoreViewModel] 取消恢复过程失败: ${e.message}")
            }
        }
    }
    
    /**
     * 重置恢复状态
     */
    fun resetState() {
        _restoreState.value = RestoreState.Idle
    }
    
    /**
     * 恢复回调实现
     */
private val restoreCallback = object : RestoreManager.ProgressCallback {
    override fun onStart(operation: String) {
        _restoreState.value = RestoreState.InProgress(operation, 0)
    }

    override fun onProgressUpdate(operation: String, progress: Int) {
        _restoreState.value = RestoreState.InProgress(operation, progress)
    }
    
    override fun onProgressUpdate(operation: String, progress: Int, message: String) {
        _restoreState.value = RestoreState.InProgress(operation, progress, message)
    }

    override fun onComplete(success: Boolean, message: String) {
        if (success) {
            _restoreState.value = RestoreState.Success(message)
        } else {
            _restoreState.value = RestoreState.Error(message)
        }
        }
    }
}

/**
 * 恢复状态密封类
 */
sealed class RestoreState {
    object Idle : RestoreState()
    data class InProgress(val operation: String, val progress: Int, val message: String = "") : RestoreState()
    data class Success(val message: String) : RestoreState()
    data class Error(val message: String) : RestoreState()
}
