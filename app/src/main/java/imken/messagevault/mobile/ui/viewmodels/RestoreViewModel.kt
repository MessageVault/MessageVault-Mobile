package imken.messagevault.mobile.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import imken.messagevault.mobile.api.ApiClient
import imken.messagevault.mobile.config.Config
import imken.messagevault.mobile.data.models.BackupFile
import imken.messagevault.mobile.data.RestoreManager
import imken.messagevault.mobile.models.RestoreState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Date

/**
 * 恢复功能视图模型
 * 
 * 管理恢复屏幕的状态和业务逻辑，遵循MVVM架构模式
 * 
 * @param context 应用上下文
 */
class RestoreViewModel(
    private val context: Context
) : ViewModel() {
    
    // 组件依赖
    private val config = Config.getInstance(context)
    private val apiClient = ApiClient(config) 
    private val restoreManager = RestoreManager(context, config, apiClient)
    
    // 备份文件列表
    private val _backupFiles = MutableStateFlow<List<BackupFile>>(emptyList())
    val backupFiles: StateFlow<List<BackupFile>> = _backupFiles.asStateFlow()
    
    // 选中的备份文件
    private val _selectedBackupFile = MutableStateFlow<BackupFile?>(null)
    val selectedBackupFile: StateFlow<BackupFile?> = _selectedBackupFile.asStateFlow()
    
    // 恢复状态信息
    val restoreStatus = mutableStateOf<String?>(null)
    
    // 操作进行中状态
    val isOperating = mutableStateOf(false)
    
    // 权限授予状态
    val permissionsGranted = mutableStateOf(false)
    
    // 修复第39行的状态流引用问题
    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState

    // 修复第54行的SomeType和initialValue引用
    // 定义一个具体的类型并提供初始值
    private val _restoreProgress = MutableStateFlow(0)
    val restoreProgress: StateFlow<Int> = _restoreProgress.asStateFlow()
    
    private val TAG = "RestoreViewModel"
    
    /**
     * 初始化视图模型
     * 
     * 加载可用的备份文件
     */
    init {
        loadBackupFiles()
    }
    
    /**
     * 加载备份文件列表
     */
    fun loadBackupFiles() {
        viewModelScope.launch {
            isOperating.value = true
            
            try {
                val files = restoreManager.getAvailableBackups()
                _backupFiles.value = files
                
                if (files.isEmpty()) {
                    restoreStatus.value = "未找到备份文件"
                    Timber.i("[Mobile] INFO [Restore] 未找到备份文件; Context: 加载备份文件列表")
                } else {
                    restoreStatus.value = null
                    Timber.i("[Mobile] INFO [Restore] 加载了 ${files.size} 个备份文件; Context: 加载备份文件列表")
                }
            } catch (e: Exception) {
                restoreStatus.value = "加载备份文件失败: ${e.message}"
                Timber.e(e, "[Mobile] ERROR [Restore] 加载备份文件失败; Context: ${e.message}")
            } finally {
                isOperating.value = false
            }
        }
    }
    
    /**
     * 选择备份文件
     * 
     * @param backupFile 要选择的备份文件
     */
    fun selectBackupFile(backupFile: BackupFile) {
        _selectedBackupFile.value = backupFile
        Timber.d("[Mobile] DEBUG [Restore] 选择了备份文件 ${backupFile.fileName}; Context: 用户操作")
    }
    
    /**
     * 恢复备份文件
     * 
     * @param backupFile 要恢复的备份文件
     */
    fun restoreBackupFile(backupFile: BackupFile) {
        if (isOperating.value) {
            Timber.w("[Mobile] WARN [Restore] 已有操作正在进行; Context: 用户重复点击")
            return
        }
        
        viewModelScope.launch {
            isOperating.value = true
            restoreStatus.value = "正在恢复备份..."
            
            try {
                Timber.i("%tFT%<tT.%<tLZ [Mobile] INFO [Restore] 开始恢复备份 ${backupFile.fileName}; Context: 用户操作", Date())
                
                val result = restoreManager.restoreFromFile(backupFile)
                
                withContext(Dispatchers.Main) {
                    isOperating.value = false
                    
                    if (result.success) {
                        restoreStatus.value = result.message
                        Timber.i("[Mobile] INFO [Restore] 恢复备份成功; Context: ${result.message}")
                    } else {
                        restoreStatus.value = result.message
                        Timber.e("[Mobile] ERROR [Restore] 恢复备份失败; Context: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isOperating.value = false
                    restoreStatus.value = "恢复失败: ${e.message}"
                    Timber.e(e, "[Mobile] ERROR [Restore] 恢复过程异常; Context: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 设置权限状态
     * 
     * @param granted 是否已授权
     */
    fun setPermissionsGranted(granted: Boolean) {
        permissionsGranted.value = granted
        if (granted) {
            loadBackupFiles()
        }
    }
    
    /**
     * RestoreViewModel工厂类
     * 
     * 用于创建携带Context参数的ViewModel实例
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RestoreViewModel::class.java)) {
                return RestoreViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    // 日志辅助方法
    fun logError(message: String, exception: Exception? = null) {
        Timber.e(exception, message)
    }
}
