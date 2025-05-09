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
import imken.messagevault.mobile.data.models.BackupFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.vector.ImageVector

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
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        
        if (allGranted) {
            Timber.i("[Mobile] INFO [Permission] 已获取所有必要权限; Context: 用户授权成功")
        } else {
            Timber.w("[Mobile] WARN [Permission] 权限请求被拒绝; Context: 用户拒绝了部分或全部权限请求")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Timber.d("[Mobile] DEBUG [Lifecycle] MainActivity onCreate; Context: 应用启动")
        
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
            requestPermissionsLauncher.launch(permissionsToCheck)
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

