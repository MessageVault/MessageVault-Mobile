package imken.messagevault.mobile.ui.viewmodels

import android.content.Context
import android.os.Build
import android.provider.Telephony
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import imken.messagevault.mobile.api.ApiClient
import imken.messagevault.mobile.config.Config
import imken.messagevault.mobile.data.RestoreManager
import imken.messagevault.mobile.models.BackupFile
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
    /**
     * 是否需要设置为默认短信应用的状态标志
     * 
     * 此状态用于控制是否显示"需要设置为默认短信应用"的提示对话框。当用户尝试恢复包含短信的备份，
     * 但应用尚未被设置为默认短信应用时，此状态会被设置为 true。
     * 
     * 状态值说明:
     * - true: 表示需要向用户显示权限请求对话框，应用需要成为默认短信应用才能继续
     * - false: 表示不需要显示权限请求对话框，可以直接进行恢复操作
     * 
     * 更新时机:
     * - 在 restoreBackupFile 方法中检测到需要默认短信权限时设置为 true
     * - 用户拒绝设置为默认短信应用后，保持为 true
     * - 用户同意设置为默认短信应用且设置成功后，设置为 false
     * - 用户取消权限请求对话框后，设置为 false
     * 
     * 与 MainActivity 交互:
     * MainActivity 可以通过 notifyDefaultSmsAppChanged 方法通知 ViewModel 更新此状态
     */
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
     * 此方法负责从选定的备份文件恢复数据，包括短信、通话记录和联系人。
     * 由于Android系统安全限制，恢复短信需要应用被设置为默认短信应用。
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
        
        // ===== 默认短信应用权限检查部分 =====
        // 此部分代码负责检查是否需要默认短信应用权限，是权限流程的关键部分
        // 如果需要更改此部分，请确保理解整个权限请求流程，并保持与MainActivity的交互逻辑
        try {
            // 检查备份文件是否包含短信数据
            // 注意：根据当前的BackupFile模型，我们直接使用filePath属性
            val backupFilePath = backupFile.filePath
            val jsonFile = java.io.File(backupFilePath)
            if (jsonFile.exists() && jsonFile.canRead()) {
                val jsonText = jsonFile.readText()
                val containsSms = jsonText.contains("\"sms\"") || jsonText.contains("\"messages\"")
                
                Timber.d("[Mobile] DEBUG [Restore] 备份文件解析: 包含短信=${containsSms}, 是默认短信应用=${isDefault}")
                
                // 如果备份包含短信且应用不是默认短信应用，则需要请求权限
                if (containsSms && !isDefault) {
                    Timber.w("[Mobile] WARN [Restore] 需要设置为默认短信应用; Context: 用户尝试恢复包含短信的备份")
                    needDefaultSmsApp.value = true
                    return  // 终止恢复流程，等待用户授予权限
                }
            } else {
                Timber.e("[Mobile] ERROR [Restore] 备份文件不存在或无法读取; Path: $backupFilePath")
                if (!isDefault) {
                    needDefaultSmsApp.value = true
                    return  // 如果无法确定是否包含短信，为安全起见假定包含，终止恢复流程
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Restore] 检查备份文件是否包含短信时出错; Context: 预检查")
            // 如果无法确定是否包含短信，为安全起见假定包含
            if (!isDefault) {
                needDefaultSmsApp.value = true
                return  // 终止恢复流程，等待用户授予权限
            }
        }
        // ===== 默认短信应用权限检查部分结束 =====
        
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
                    val progressCallback = object : RestoreManager.ProgressCallback {
                        override fun onStart(operation: String) {
                            restorePhase.value = operation
                            _restoreState.value = RestoreState.InProgress(20, operation)
                            Timber.d("[Mobile] DEBUG [Restore] 开始操作: $operation")
                        }
                        
                        override fun onProgressUpdate(operation: String, progress: Int) {
                            val calculatedProgress = 20 + (progress * 0.8).toInt() // 20% 到 100% 的范围
                            _restoreProgress.value = calculatedProgress
                            restorePhase.value = operation
                            _restoreState.value = RestoreState.InProgress(calculatedProgress, operation)
                            Timber.d("[Mobile] DEBUG [Restore] 进度更新: 阶段=$operation, 进度=$progress%")
                        }
                        
                        override fun onProgressUpdate(operation: String, progress: Int, message: String) {
                            val calculatedProgress = 20 + (progress * 0.8).toInt() // 20% 到 100% 的范围
                            _restoreProgress.value = calculatedProgress
                            restorePhase.value = "$operation: $message"
                            _restoreState.value = RestoreState.InProgress(calculatedProgress, "$operation: $message")
                            Timber.d("[Mobile] DEBUG [Restore] 进度更新: 阶段=$operation, 进度=$progress%, 详情=$message")
                        }
                        
                        override fun onComplete(success: Boolean, message: String) {
                            _restoreProgress.value = 100
                            if (success) {
                                restorePhase.value = "恢复完成"
                                _restoreState.value = RestoreState.Completed(true, message)
                                Timber.i("[Mobile] INFO [Restore] 恢复完成: $message")
                            } else {
                                restorePhase.value = "恢复失败"
                                _restoreState.value = RestoreState.Error(message)
                                Timber.e("[Mobile] ERROR [Restore] 恢复失败: $message")
                            }
                        }
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
            // 先尝试使用RoleManager（Android 10+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                    if (roleManager != null) {
                        val hasRole = roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
                        Timber.d("[Mobile] DEBUG [Restore] RoleManager检查结果: $hasRole")
                        if (hasRole) {
                            return true
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 使用RoleManager检查权限失败")
                }
            }
            
            // 再尝试传统方法
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            
            // 处理系统返回null的特殊情况
            if (defaultSmsPackage == null) {
                Timber.w("[Mobile] WARN [Restore] 系统返回了null作为默认短信应用包名，尝试使用替代方法检查")
                
                // 尝试检查应用是否有接收SMS的权限
                val hasReceiveSmsPermission = context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasSendSmsPermission = context.checkSelfPermission(android.Manifest.permission.SEND_SMS) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                    
                val hasPermissions = hasReceiveSmsPermission && hasSendSmsPermission
                
                // 检查MainActivity的报告
                val mainActivityReportedAsDefault = 
                    context.getSharedPreferences("sms_app_status", Context.MODE_PRIVATE)
                        .getBoolean("is_default_sms_app", false)
                
                Timber.d("[Mobile] DEBUG [Restore] 替代检查 - 权限状态: $hasPermissions, MainActivity报告: $mainActivityReportedAsDefault")
                
                // 如果MainActivity报告为默认且有权限，则认为是默认应用
                if (mainActivityReportedAsDefault && hasPermissions) {
                    Timber.i("[Mobile] INFO [Restore] 虽然系统API返回null，但MainActivity报告应用是默认短信应用且拥有必要权限")
                    return true
                }
                
                // 对于模拟器和Android SDK环境的特殊处理
                if (Build.PRODUCT.contains("sdk") || Build.BRAND.contains("generic")) {
                    Timber.i("[Mobile] INFO [Restore] 检测到模拟器或SDK环境，假定应用有默认短信权限")
                    // 将这个状态保存在SharedPreferences中供其他组件使用
                    context.getSharedPreferences("sms_app_status", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_default_sms_app", true)
                        .apply()
                    return true
                }
                
                return false
            }
            
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
     * 
     * 此方法用于重置 needDefaultSmsApp 状态为 false，通常在以下情况下调用:
     * 1. 用户取消设置默认短信应用的对话框
     * 2. 用户已完成设置默认短信应用的流程
     * 3. 需要清除旧的权限请求状态
     * 
     * 方法执行流程:
     * 1. 将 needDefaultSmsApp 重置为 false
     * 2. 检查当前应用是否为默认短信应用
     * 3. 记录日志，包含当前的默认短信应用状态
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