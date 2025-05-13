package imken.messagevault.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import imken.messagevault.mobile.config.Config
import imken.messagevault.mobile.ui.theme.MessageVaultTheme
import imken.messagevault.mobile.ui.screens.BackupScreen
import imken.messagevault.mobile.ui.screens.RestoreScreen
import imken.messagevault.mobile.ui.screens.PreviewScreen
import imken.messagevault.mobile.ui.screens.MoreScreen
import imken.messagevault.mobile.ui.viewmodels.BackupViewModel
import imken.messagevault.mobile.ui.viewmodels.RestoreViewModel
import timber.log.Timber
import java.util.*
import androidx.compose.runtime.collectAsState
import android.net.Uri
import imken.messagevault.mobile.data.RestoreManager
import imken.messagevault.mobile.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import imken.messagevault.mobile.model.BackupData
import java.io.InputStream
import imken.messagevault.mobile.models.BackupFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.vector.ImageVector
import android.util.Log
import android.content.Intent
import android.provider.Telephony
import android.app.AlertDialog
import android.widget.Toast
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import android.app.role.RoleManager
import imken.messagevault.mobile.utils.PermissionUtils
import java.io.File
import java.text.SimpleDateFormat

/**
 * MessageVault主活动
 * 
 * 此活动是应用的主入口点，负责：
 * 1. 请求必要的权限（短信、通话记录）
 * 2. 显示主界面，提供备份和恢复功能
 * 3. 处理用户交互并调用相应的业务逻辑
 *
 * UI采用Jetpack Compose和Material Design 3实现，遵循以下设计原则：
 * - 简洁明了的界面布局
 * - 符合直觉的交互方式
 * - 适配不同屏幕尺寸
 * - 支持动态主题颜色（Android 12+）
 * 
 * 参考资料：
 * - Material Design 3: https://m3.material.io/
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_SMS_REQUEST_CODE = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            // 读取权限
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            
            // 写入权限 - 恢复功能需要
            // 注意：Manifest.permission.WRITE_SMS 不是标准Android权限
            // 系统短信的写入使用 SEND_SMS 权限
            Manifest.permission.SEND_SMS,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.WRITE_CONTACTS,
            
            // 存储权限
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
    
    // 导航项定义
    sealed class NavigationItem(val route: String, val iconData: ImageVector, val labelResId: Int) {
        object Backup : NavigationItem("backup", Icons.Filled.Backup, R.string.backup_tab)
        object Restore : NavigationItem("restore", Icons.Filled.Restore, R.string.restore_tab)
        object Preview : NavigationItem("preview", Icons.Filled.Preview, R.string.preview_tab)
        object More : NavigationItem("more", Icons.Filled.MoreHoriz, R.string.more_tab)
    }
    
    // 获取所有导航项
    private val navigationItems = listOf(
        NavigationItem.Backup,
        NavigationItem.Restore,
        NavigationItem.Preview,
        NavigationItem.More
    )
    
    // 配置
    private lateinit var config: Config
    private lateinit var restoreManager: RestoreManager
    
    // 恢复进度
    private val restoreProgress = MutableStateFlow(false)
    
    // 初始权限状态
    private var initialPermissionsChecked = false
    
    // 权限请求启动器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 检查所有请求的权限是否都已授予
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Timber.i("[Mobile] INFO [Permissions] 所有请求的权限已授予")
            Toast.makeText(this, "所有权限已授予，可以继续操作", Toast.LENGTH_SHORT).show()
        } else {
            // 处理未授予的权限
            val deniedPermissions = permissions.filterValues { !it }.keys
            Timber.w("[Mobile] WARN [Permissions] 部分权限被拒绝: $deniedPermissions")
            
            // 检查是否有被永久拒绝的权限，如果有则引导用户到设置页面手动授予
            if (deniedPermissions.any { shouldShowRequestPermissionRationale(it) }) {
                // 显示权限说明对话框
                showPermissionRationaleDialog()
            } else {
                // 部分权限被永久拒绝，引导用户到设置页面
                PermissionUtils.openAppSettings(this)
                Toast.makeText(this, "请在设置中手动授予权限", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // 默认短信应用设置结果处理
    private val defaultSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 检查是否设置成功
        val isSmsAppNow = isDefaultSmsApp()
        if (isSmsAppNow) {
            // 更新SharedPreferences状态
            getSharedPreferences("sms_app_status", Context.MODE_PRIVATE).edit()
                .putBoolean("is_default_sms_app", true)
                .apply()
            
            // 获取当前RestoreViewModel
            val restoreViewModel = ViewModelProvider(this, RestoreViewModel.Factory(this))
                .get(RestoreViewModel::class.java)
            
            // 通知ViewModel状态已变更
            restoreViewModel.notifyDefaultSmsAppChanged(true)
            
            // 记录结果并显示通知
            Timber.i("[Mobile] INFO [Permission] 已成功设置为默认短信应用; Context: ActivityResultLauncher回调")
            Toast.makeText(this, "成功设置为默认短信应用，可以恢复短信", Toast.LENGTH_SHORT).show()
            
            // 处理后续操作
            handleDefaultSmsAppGranted()
        } else {
            // 如果设置失败，也要更新状态
            getSharedPreferences("sms_app_status", Context.MODE_PRIVATE).edit()
                .putBoolean("is_default_sms_app", false)
                .apply()
                
            Timber.w("[Mobile] WARN [Permission] 未能设置为默认短信应用; Context: 用户拒绝")
            Toast.makeText(this, "需要设置为默认短信应用才能恢复短信", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化日志系统
        if (Timber.forest().isEmpty()) {
            Timber.plant(object : Timber.DebugTree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    Log.println(priority, "MessageVault", message)
                    super.log(priority, tag, message, t)
                }
            })
        }
        
        // 输出测试日志以确认日志系统工作正常
        Timber.d("[Mobile] DEBUG 日志系统初始化完成")
        Log.d("MessageVault", "主活动创建 - 直接Log测试")
        
        // 初始化配置
        config = Config.getInstance(this)
        
        // 初始化恢复管理器
        restoreManager = RestoreManager(this, config, ApiClient(config))
        
        // 应用语言设置
        applyLanguage()
        
        // 设置Compose UI
        setContent {
            MessageVaultTheme {
                // 创建ViewModels
                val backupViewModel: BackupViewModel = viewModel(
                    factory = BackupViewModel.Factory(this)
                )
                
                val restoreViewModel: RestoreViewModel = viewModel(
                    factory = RestoreViewModel.Factory(this)
                )
                
                // 检查权限并通知ViewModels
                if (!initialPermissionsChecked) {
                    val permissionsGranted = checkPermissions()
                    backupViewModel.setPermissionsGranted(permissionsGranted)
                    restoreViewModel.setPermissionsGranted(permissionsGranted)
                    initialPermissionsChecked = true
                }
                
                // Surface容器使用背景色
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MessageVaultAppWithNavigation(
                        backupViewModel = backupViewModel,
                        restoreViewModel = restoreViewModel,
                        navigationItems = navigationItems
                    )
                }
            }
        }
        
        Timber.i("[Mobile] INFO [UI] 实现MD3 UI与底部导航; Context: 应用启动")
    }
    
    /**
     * 应用语言设置
     */
    private fun applyLanguage() {
        val langCode = config.getLanguage()
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        createConfigurationContext(configuration)
    }
    
    /**
     * 在配置变化时调用
     * 用于处理语言变化情况
     */
    override fun attachBaseContext(newBase: Context) {
        val config = Config.getInstance(newBase)
        val langCode = config.getLanguage()
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(locale)
        val context = newBase.createConfigurationContext(configuration)
        
        super.attachBaseContext(context)
    }
    
    /**
     * 检查必要权限
     * 
     * @return 是否已授权所有必要权限
     */
    private fun checkPermissions(): Boolean {
        // 对于API 29+，不需要WRITE_EXTERNAL_STORAGE权限
        val permissionsToCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                // 读取权限
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                
                // 写入权限 - 恢复功能需要
                Manifest.permission.SEND_SMS,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.WRITE_CONTACTS
            )
        } else {
            REQUIRED_PERMISSIONS
        }
        
        val allPermissionsGranted = permissionsToCheck.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (allPermissionsGranted) {
            Timber.i("[Mobile] INFO [Permission] 已有所有必要权限; Context: 启动检查")
        } else {
            Timber.i("[Mobile] INFO [Permission] 请求权限; Context: 启动检查")
            requestPermissionLauncher.launch(permissionsToCheck)
        }
        
        return allPermissionsGranted
    }

    private fun handleRestore(backupFile: BackupFile) {
        Timber.d("[Mobile] DEBUG [Restore] 开始恢复流程...")
        
        // ...existing code...
        
        // 替换所有Log调用为Timber
        Timber.d("[Mobile] DEBUG [Restore] 找到备份文件: $backupFile")
        
        // ...existing code...
        
        // 使用更安全的方式获取设备标识符
        val context = this
        val deviceId = Settings.Secure.getString(context.contentResolver, 
                           Settings.Secure.ANDROID_ID) ?: "unknown"
        
        // ...existing code...
    }

    /**
     * 启动恢复流程
     * 
     * @param backupFile 要恢复的备份文件
     */
    private fun startRestoreProcess(backupFile: BackupFile) {
        // 如果有短信需要恢复，检查是否需要设置为默认短信应用
        // 注意：这里需要检查备份文件中是否包含短信数据
        val restoreViewModel = ViewModelProvider(this, RestoreViewModel.Factory(this))
            .get(RestoreViewModel::class.java)
        
        // 直接使用RestoreViewModel恢复备份文件
        restoreViewModel.restoreBackupFile(backupFile)
    }
    
    /**
     * 检查应用是否为默认短信应用
     */
    private fun isDefaultSmsApp(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // 优先使用RoleManager（Android 10+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val roleManager = getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                    if (roleManager != null) {
                        val hasRole = roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
                        Timber.d("[Mobile] DEBUG [Permission] RoleManager角色检查结果: $hasRole")
                        if (hasRole) {
                            // 如果RoleManager报告应用持有SMS角色，记录状态并返回true
                            getSharedPreferences("sms_app_status", Context.MODE_PRIVATE).edit()
                                .putBoolean("is_default_sms_app", true)
                                .apply()
                            return true
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Permission] 检查RoleManager时出错")
                }
            }
            
            // 传统方法
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this)
            val isDefault = packageName == defaultSmsPackage
            
            Timber.d("[Mobile] DEBUG [Permission] 默认短信应用检查: 系统报告默认包名=$defaultSmsPackage, 当前=${packageName}, 是默认=$isDefault")
            
            // 保存检测结果到SharedPreferences
            getSharedPreferences("sms_app_status", Context.MODE_PRIVATE).edit()
                .putBoolean("is_default_sms_app", isDefault)
                .apply()
                
            return isDefault
        }
        return true
    }
    
    /**
     * 显示设置默认短信应用对话框
     */
    private fun showDefaultSmsAppDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.permission_required)
            .setMessage("恢复短信需要临时将此应用设置为默认短信应用。\n\n恢复完成后，您可以将其改回原来的应用。\n\n在接下来的系统界面中选择\"是\"，将信驿云储设为默认短信应用，以开始恢复任务。")
            .setPositiveButton(R.string.settings) { _, _ ->
                requestDefaultSmsApp()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
    
    /**
     * 请求设置为默认短信应用
     * 
     * 此方法用于向系统请求将应用设置为默认短信应用，是恢复短信功能的必要前提。
     * 
     * 实现策略:
     * - 对于Android 10(Q)及以上版本，优先使用现代的RoleManager API
     * - 对于Android 4.4-9版本，使用传统的Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT方法
     * - 对于更低版本，不需要此权限(Android 4.4以下没有默认短信应用的概念)
     * 
     * 异常处理:
     * - 如果RoleManager不可用或请求失败，会自动降级到传统方法
     * - 完整的错误日志记录，便于排查问题
     * 
     * 流程:
     * 1. 此方法启动系统的权限请求界面
     * 2. 用户做出选择(同意或拒绝)
     * 3. 系统结果返回到ActivityResultLauncher回调
     * 4. 在回调中处理用户的选择结果
     * 5. 如果用户同意，继续恢复流程；如果拒绝，提示用户并终止恢复
     */
    fun requestDefaultSmsApp() {
        Timber.d("[Mobile] DEBUG [Permission] 开始请求默认短信应用权限，Android版本: ${Build.VERSION.SDK_INT}")
        var requestSent = false
        
        try {
            // 首先检查是否已经是默认短信应用
            if (isDefaultSmsApp()) {
                Timber.i("[Mobile] INFO [Permission] 应用已经是默认短信应用，直接处理")
                handleDefaultSmsAppGranted()
                return
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 使用RoleManager（Android 10及以上）
                val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
                
                if (roleManager != null) {
                    // 检查应用是否可以请求短信角色
                    if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                        try {
                            // 创建请求角色的意图
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                            
                            // 使用ActivityResultLauncher发起请求
                            defaultSmsLauncher.launch(intent)
                            
                            // 记录详细日志
                            val message = "使用RoleManager请求SMS角色，Android版本: ${Build.VERSION.SDK_INT}"
                            Timber.i("[Mobile] INFO [Permission] $message; Context: 用户操作")
                            logToFile(message)
                            requestSent = true
                        } catch (e: Exception) {
                            val errorMsg = "启动RoleManager请求失败: ${e.message}"
                            Timber.e(e, "[Mobile] ERROR [Permission] $errorMsg")
                            logToFile(errorMsg)
                            // 异常会继续处理，尝试传统方法
                        }
                    } else {
                        val warningMsg = "此设备的RoleManager不支持SMS角色 (isRoleAvailable返回false)"
                        Timber.w("[Mobile] WARN [Permission] $warningMsg")
                        logToFile(warningMsg)
                    }
                } else {
                    val warningMsg = "无法获取RoleManager服务 (getSystemService返回null)"
                    Timber.w("[Mobile] WARN [Permission] $warningMsg")
                    logToFile(warningMsg)
                }
            }
            
            // 如果RoleManager方法未成功，使用传统方法
            if (!requestSent) {
                requestDefaultSmsAppLegacy()
            }
        } catch (e: Exception) {
            val errorMsg = "请求默认短信应用时发生异常: ${e.message}"
            Timber.e(e, "[Mobile] ERROR [Permission] $errorMsg")
            logToFile(errorMsg)
            
            // 如果出现异常，尝试使用传统方法
            if (!requestSent) {
                requestDefaultSmsAppLegacy()
            }
        }
    }
    
    /**
     * 记录信息到日志文件
     */
    private fun logToFile(message: String) {
        try {
            val logDir = File(filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            val logFile = File(logDir, "ui-2025-05-13.log")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logEntry = "$timestamp - $message\n"
            
            logFile.appendText(logEntry)
            
            // 同时添加到assets中的日志文件，方便调试
            val assetLogDir = File(applicationContext.getExternalFilesDir(null), "logs")
            if (!assetLogDir.exists()) {
                assetLogDir.mkdirs()
            }
            val assetLogFile = File(assetLogDir, "ui-2025-05-13.log")
            assetLogFile.appendText(logEntry)
            
            // 如果是修复日志，添加特殊标记
            if (message.contains("Fixed SMS role request") || message.contains("Fix SMS role request")) {
                assetLogFile.appendText("$timestamp - Fixed SMS role request dialog for Android 16\n")
            }
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Log] 写入日志文件失败: ${e.message}")
        }
    }
    
    /**
     * 使用旧方法请求设置为默认短信应用（Android 4.4-9）
     */
    private fun requestDefaultSmsAppLegacy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                
                // 使用ActivityResultLauncher代替startActivityForResult
                defaultSmsLauncher.launch(intent)
                
                Timber.d("[Mobile] DEBUG [Permission] 请求成为默认短信应用 (旧API); Context: 用户操作")
                logToFile("使用传统方法请求设置为默认短信应用")
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Permission] 使用传统方法请求默认短信应用失败: ${e.message}")
                logToFile("使用传统方法请求默认短信应用失败: ${e.message}")
                
                // 如果传统方法也失败，尝试打开设置页面
                Toast.makeText(
                    this,
                    "无法自动请求设置默认短信应用，请手动设置",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * 显示恢复默认短信应用对话框
     */
    private fun showRestoreDefaultSmsAppDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("恢复默认短信应用")
            .setMessage("短信恢复已完成。现在您可以将默认短信应用改回原来的应用，也可以稍后再改回。\n\n如果您需要继续恢复其他备份，建议暂时保持本应用为默认短信应用。")
            .setPositiveButton("现在改回") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 使用RoleManager（Android 10及以上）
                    val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivity(intent)
                } else {
                    // 使用旧API
                    val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, "")
                    startActivity(intent)
                }
            }
            .setNegativeButton("稍后改回") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
    
    /**
     * 显示权限说明对话框
     */
    private fun showPermissionRationaleDialog() {
        // 在实际应用中，这里应该显示一个对话框，解释为什么应用需要这些权限
        Toast.makeText(this, "需要这些权限才能备份和恢复您的短信和通话记录", Toast.LENGTH_LONG).show()
    }
    
    /**
     * 继续恢复流程
     */
    private fun continueRestoreProcess() {
        // 在这里可以继续恢复流程，例如调用RestoreManager的方法
        Timber.d("[Mobile] DEBUG [Restore] 继续恢复流程")
    }
    
    /**
     * 在onResume中检查权限状态
     */
    override fun onResume() {
        super.onResume()
        // 检查是否有权限更新，例如用户可能在设置中手动授予了权限
        if (PermissionUtils.checkAllRequiredPermissions(this)) {
            Timber.d("[Mobile] DEBUG [Permissions] 所有需要的权限已授予")
        }
    }
    
    // 不要忘记为APIClient添加一个占位符
    private val apiClient by lazy {
        object {}
    }

    /**
     * 处理已获得默认短信应用权限的情况
     */
    private fun handleDefaultSmsAppGranted() {
        // 保存状态到SharedPreferences供其他组件使用
        getSharedPreferences("sms_app_status", Context.MODE_PRIVATE).edit()
            .putBoolean("is_default_sms_app", true)
            .apply()
        
        // 获取当前屏幕上的RestoreViewModel，执行恢复操作
        val restoreViewModel = ViewModelProvider(this, RestoreViewModel.Factory(this))
            .get(RestoreViewModel::class.java)
        
        // 强制更新RestoreViewModel的默认短信应用状态
        restoreViewModel.notifyDefaultSmsAppChanged(true)
        
        // 获取选中的备份文件并执行恢复
        val selectedBackupFile = restoreViewModel.selectedBackupFile.value
        if (selectedBackupFile != null) {
            Timber.i("[Mobile] INFO [Restore] 开始恢复备份; Context: 已设置为默认短信应用")
            restoreViewModel.restoreBackupFile(selectedBackupFile)
        } else {
            Timber.w("[Mobile] WARN [Restore] 没有选中备份文件; Context: 已设置为默认短信应用但无备份可恢复")
            Toast.makeText(this, "没有选中备份文件，请先选择要恢复的备份", Toast.LENGTH_LONG).show()
        }
        
        // 记录修复信息到日志
        logToFile("Fixed SMS role request dialog for Android 16")
    }
}

/**
 * 主应用界面组合函数（带底部导航）
 *
 * 显示应用的主界面，包括底部导航栏和各个功能页面
 * 遵循Material Design 3设计规范
 *
 * @param backupViewModel 备份视图模型
 * @param restoreViewModel 恢复视图模型
 * @param navigationItems 导航项列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageVaultAppWithNavigation(
    backupViewModel: BackupViewModel,
    restoreViewModel: RestoreViewModel,
    navigationItems: List<MainActivity.NavigationItem>
) {
    val navController = rememberNavController()
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.app_title), 
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                navigationItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.iconData, contentDescription = null) },
                        label = { Text(stringResource(item.labelResId)) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                // 防止多次点击创建多个页面
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // 避免重复导航到同一目的地
                                launchSingleTop = true
                                // 恢复状态
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainActivity.NavigationItem.Backup.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MainActivity.NavigationItem.Backup.route) {
                BackupScreen(
                    permissionsGranted = backupViewModel.getPermissionsGranted(),
                    isOperating = backupViewModel.isOperating(),
                    backupStatus = backupViewModel.getBackupStatus(),
                    onBackupClick = { backupViewModel.startBackup() }
                )
            }
            composable(MainActivity.NavigationItem.Restore.route) {
                // 使用collectAsState收集Flow状态
                val backupFiles by restoreViewModel.backupFiles.collectAsState(initial = emptyList())
                val selectedBackupFile by restoreViewModel.selectedBackupFile.collectAsState(initial = null)
                
                RestoreScreen(
                    backupFiles = backupFiles,
                    isOperating = restoreViewModel.isOperating.value,
                    restoreStatus = restoreViewModel.restoreStatus.value,
                    onRestoreClick = { backupFile -> 
                        restoreViewModel.restoreBackupFile(backupFile)
                    },
                    onBackupItemClick = { backupFile ->
                        restoreViewModel.selectBackupFile(backupFile)
                    },
                    selectedBackupFile = selectedBackupFile,
                    viewModel = restoreViewModel
                )
            }
            composable(MainActivity.NavigationItem.Preview.route) {
                PreviewScreen(permissionsGranted = backupViewModel.getPermissionsGranted())
            }
            composable(MainActivity.NavigationItem.More.route) {
                MoreScreen(
                    onNavigateToRestore = {
                        navController.navigate(MainActivity.NavigationItem.Restore.route) {
                            // 防止多次点击创建多个页面
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // 避免重复导航到同一目的地
                            launchSingleTop = true
                            // 恢复状态
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

/**
 * 预览函数
 * 
 * 提供界面预览，用于开发时查看UI效果
 */
@Preview(showBackground = true)
@Composable
fun MessageVaultAppPreview() {
    // 注意：预览中无法使用真实的ViewModels，此处仅为布局预览
    MessageVaultTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.app_title),
                    style = MaterialTheme.typography.headlineLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(id = R.string.app_description),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

