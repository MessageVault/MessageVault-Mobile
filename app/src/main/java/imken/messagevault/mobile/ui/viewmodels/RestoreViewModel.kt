package imken.messagevault.mobile.ui.viewmodels

import android.content.Context
import android.provider.Telephony
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import imken.messagevault.mobile.api.ApiClient
import imken.messagevault.mobile.config.Config
import imken.messagevault.mobile.data.RestoreManager
import imken.messagevault.mobile.models.RestoreState
import imken.messagevault.mobile.models.BackupFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Date
import java.io.File

/**
 * 恢复功能视图模型
 * 
 * 管理恢复屏幕的状态和业务逻辑，遵循MVVM架构模式
 * 
 * @param context 应用上下文
 */
class RestoreViewModel(
    private val context: Context,
    private val restoreManager: RestoreManager
) : ViewModel() {
    
    // 组件依赖
    private val config = Config.getInstance(context)
    private val apiClient = ApiClient(config) 
    
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
    
    // 恢复状态
    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    // 恢复进度
    private val _restoreProgress = MutableStateFlow(0)
    val restoreProgress: StateFlow<Int> = _restoreProgress.asStateFlow()
    
    // 是否需要设置为默认短信应用
    val needDefaultSmsApp = mutableStateOf(false)
    
    // 强制覆盖默认短信应用状态的标记，用于处理系统返回不一致的情况
    private var forceDefaultSmsAppStatus: Boolean? = null
    
    // 恢复的阶段说明
    val restorePhase = mutableStateOf<String?>(null)
    
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
     * 直接通知默认短信应用状态变化
     * 用于从MainActivity强制更新状态
     */
    fun notifyDefaultSmsAppChanged(isDefault: Boolean) {
        forceDefaultSmsAppStatus = isDefault
        Timber.i("[Mobile] INFO [Restore] 强制更新默认短信应用状态: isDefault=$isDefault")
        
        // 如果已设为默认应用但仍显示需要权限，自动重置状态
        if (isDefault && needDefaultSmsApp.value) {
            needDefaultSmsApp.value = false
            Timber.i("[Mobile] INFO [Restore] 自动重置权限检查对话框，当前被强制设为默认短信应用")
        }
    }
    
    /**
     * 加载备份文件列表
     */
    fun loadBackupFiles() {
        if (isOperating.value) {
            Timber.w("[Mobile] WARN [Restore] 已有操作正在进行中，跳过加载备份; Context: 用户请求重复操作")
            return
        }
        
        viewModelScope.launch {
            try {
                Timber.i("[Mobile] INFO [Restore] 开始加载备份文件; Context: 用户打开恢复页面")
                
                val files = restoreManager.getAvailableBackups()
                _backupFiles.value = files
                
                Timber.i("[Mobile] INFO [Restore] 加载备份文件完成; Context: 找到 ${files.size} 个备份文件")
                
                // 如果有备份文件，默认选择第一个
                if (files.isNotEmpty() && _selectedBackupFile.value == null) {
                    _selectedBackupFile.value = files[0]
                }
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Restore] 加载备份文件失败; Context: ${e.message}")
                restoreStatus.value = "加载备份文件失败: ${e.message}"
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
        // 在日志中强制记录当前是否为默认短信应用
        val isDefault = isDefaultSmsApp()
        Timber.i("[Mobile] INFO [Restore] 开始恢复，是否为默认短信应用: $isDefault")
        
        if (isOperating.value) {
            Timber.w("[Mobile] WARN [Restore] 已有操作正在进行; Context: 用户重复点击")
            return
        }
        
        // 检查是否需要默认短信应用权限
        // 先解析备份文件以检查是否包含SMS数据
        try {
            // 检查备份文件是否包含短信数据
            // 注意：根据当前的BackupFile模型，我们直接使用filePath属性
            val backupFilePath = backupFile.filePath
            val jsonFile = File(backupFilePath)
            if (jsonFile.exists() && jsonFile.canRead()) {
                val jsonText = jsonFile.readText()
                val containsSms = jsonText.contains("\"sms\"") || jsonText.contains("\"messages\"")
                
                Timber.d("[Mobile] DEBUG [Restore] 备份文件解析: 包含短信=${containsSms}, 是默认短信应用=${isDefault}")
                
                if (containsSms && !isDefault) {
                    Timber.w("[Mobile] WARN [Restore] 需要设置为默认短信应用; Context: 用户尝试恢复包含短信的备份")
                    needDefaultSmsApp.value = true
                    return
                }
            } else {
                Timber.e("[Mobile] ERROR [Restore] 备份文件不存在或无法读取; Path: $backupFilePath")
                if (!isDefault) {
                    needDefaultSmsApp.value = true
                    return
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Restore] 检查备份文件是否包含短信时出错; Context: 预检查")
            // 如果无法确定是否包含短信，为安全起见假定包含
            if (!isDefault) {
                needDefaultSmsApp.value = true
                return
            }
        }
        
        // 忽略WAP推送权限问题，继续恢复
        Timber.i("[Mobile] INFO [Restore] 继续恢复过程，忽略WAP推送权限问题")
        
        viewModelScope.launch {
            isOperating.value = true
            _restoreState.value = RestoreState.Preparing
            restoreStatus.value = "正在准备恢复备份..."
            _restoreProgress.value = 0
            
            try {
                Timber.i("%tFT%<tT.%<tLZ [Mobile] INFO [Restore] 开始恢复备份 ${backupFile.fileName}; Context: 用户操作", Date())
                
                // 解析备份文件内容以估算总工作量
                restorePhase.value = "正在解析备份文件..."
                _restoreProgress.value = 10
                val backupData = restoreManager.parseBackupFile(backupFile)
                
                if (backupData != null) {
                    val smsCount = backupData.messages?.size ?: 0
                    val callLogsCount = backupData.callLogs?.size ?: 0
                    val contactsCount = backupData.contacts?.size ?: 0
                    
                    Timber.i("[Mobile] INFO [Restore] 备份数据解析完成: SMS=$smsCount, CallLogs=$callLogsCount, Contacts=$contactsCount")
                    
                    _restoreProgress.value = 20
                    
                    // 更新恢复状态
                    restorePhase.value = "正在恢复短信、通话记录和联系人..."
                    _restoreState.value = RestoreState.InProgress(20, "正在恢复短信、通话记录和联系人...")
                    
                    // 创建进度更新回调
                    val progressCallback = RestoreManager.ProgressCallback { phase, progress, detail ->
                        val calculatedProgress = 20 + (progress * 0.8).toInt() // 20% 到 100% 的范围
                        _restoreProgress.value = calculatedProgress
                        restorePhase.value = "$phase: $detail"
                        _restoreState.value = RestoreState.InProgress(calculatedProgress, "$phase: $detail")
                        Timber.d("[Mobile] DEBUG [Restore] 进度更新: $phase, $progress%, $detail")
                    }
                    
                    // 调用带进度回调的恢复方法
                    val result = restoreManager.restoreFromFile(backupFile, progressCallback)
                    
                    withContext(Dispatchers.Main) {
                        isOperating.value = false
                        _restoreProgress.value = 100
                        
                        if (result.success) {
                            _restoreState.value = RestoreState.Completed(true, result.message)
                            restoreStatus.value = result.message
                            restorePhase.value = null
                            Timber.i("[Mobile] INFO [Restore] 恢复备份成功; Context: ${result.message}")
                        } else {
                            _restoreState.value = RestoreState.Completed(false, result.message)
                            restoreStatus.value = result.message
                            restorePhase.value = null
                            Timber.e("[Mobile] ERROR [Restore] 恢复备份失败; Context: ${result.message}")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isOperating.value = false
                        _restoreProgress.value = 0
                        _restoreState.value = RestoreState.Error("无法解析备份文件")
                        restoreStatus.value = "恢复失败: 无法解析备份文件"
                        restorePhase.value = null
                        Timber.e("[Mobile] ERROR [Restore] 无法解析备份文件; Context: 文件格式可能不兼容")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isOperating.value = false
                    _restoreProgress.value = 0
                    _restoreState.value = RestoreState.Error(e.message ?: "未知错误")
                    restoreStatus.value = "恢复失败: ${e.message}"
                    restorePhase.value = null
                    Timber.e(e, "[Mobile] ERROR [Restore] 恢复过程异常; Context: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 检查应用是否为默认短信应用
     */
    private fun isDefaultSmsApp(): Boolean {
        // 如果有强制设置的状态，优先使用
        if (forceDefaultSmsAppStatus != null) {
            val forcedStatus = forceDefaultSmsAppStatus!!
            Timber.d("[Mobile] DEBUG [Restore] 使用强制设置的默认短信应用状态: $forcedStatus")
            return forcedStatus
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            val isDefault = context.packageName == defaultSmsPackage
            
            // 添加强制日志记录，帮助调试
            Timber.d("[Mobile] DEBUG [Restore] 默认短信应用检查: 当前=${context.packageName}, 系统默认=$defaultSmsPackage, 是默认=${isDefault}")
            
            return isDefault
        }
        return true
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
     * 重置需要默认短信应用状态
     */
    fun resetNeedDefaultSmsApp() {
        needDefaultSmsApp.value = false
        // 强制检查一次当前是否为默认短信应用
        val isDefault = isDefaultSmsApp()
        Timber.i("[Mobile] INFO [Restore] 重置权限检查对话框，当前是否为默认短信应用: $isDefault")
    }
    
    /**
     * 检查并更新默认短信应用状态
     * 返回当前是否为默认短信应用
     */
    fun checkAndUpdateDefaultSmsAppStatus(): Boolean {
        // 清除任何之前强制设置的状态，确保从系统获取最新状态
        forceDefaultSmsAppStatus = null
        
        // 直接从系统检查一次
        val isDefault = isDefaultSmsApp()
        
        // 如果是默认应用，记录并更新强制状态
        if (isDefault) {
            forceDefaultSmsAppStatus = true
            Timber.i("[Mobile] INFO [Restore] 检测到应用已是默认短信应用，更新状态")
        }
        
        return isDefault
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
                // 创建RestoreManager实例
                val restoreManager = RestoreManager(
                    context,
                    imken.messagevault.mobile.config.Config.getInstance(context),
                    imken.messagevault.mobile.api.ApiClient(imken.messagevault.mobile.config.Config.getInstance(context))
                )
                
                return RestoreViewModel(context, restoreManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    // 日志辅助方法
    fun logError(message: String, exception: Exception? = null) {
        Timber.e(exception, message)
    }
}
