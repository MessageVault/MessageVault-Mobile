package imken.messagevault.mobile.ui.viewmodels

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import imken.messagevault.mobile.data.BackupManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 备份视图模型
 *
 * 负责处理备份相关的UI状态和业务逻辑
 */
class BackupViewModel(
    private val backupManager: BackupManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    // TAG 仅用于日志
    private val TAG = "BackupViewModel"
    
    // 权限状态
    private val _permissionsGranted = mutableStateOf(false)
    
    // 备份操作状态
    private val _isOperating = mutableStateOf(false)
    
    // 备份消息状态
    private val _backupStatus = mutableStateOf<String?>(null)
    
    // 提供对状态的访问方法
    fun getPermissionsGranted(): Boolean = _permissionsGranted.value
    fun isOperating(): Boolean = _isOperating.value
    fun getBackupStatus(): String? = _backupStatus.value
    
    /**
     * 设置权限授权状态
     */
    fun setPermissionsGranted(granted: Boolean) {
        _permissionsGranted.value = granted
    }
    
    /**
     * 开始备份流程
     */
    fun startBackup() {
        if (!_permissionsGranted.value) {
            _backupStatus.value = "权限不足，无法执行备份"
            return
        }
        
        if (_isOperating.value) {
            Timber.d("[Mobile] DEBUG [Backup] 备份操作已在进行中")
            return
        }
        
        _isOperating.value = true
        _backupStatus.value = "正在准备备份..."
        
        viewModelScope.launch(dispatcher) {
            try {
                val result = backupManager.performBackup()
                _backupStatus.value = "备份完成: ${result.messagesCount} 条短信, ${result.callLogsCount} 条通话记录, ${result.contactsCount} 个联系人"
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Backup] 备份失败")
                _backupStatus.value = "备份失败: ${e.message}"
            } finally {
                _isOperating.value = false
            }
        }
    }
    
    /**
     * 工厂类，用于创建 BackupViewModel 实例
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {
                return BackupViewModel(BackupManager(context)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
