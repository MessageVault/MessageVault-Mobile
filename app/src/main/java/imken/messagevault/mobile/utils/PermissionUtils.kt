package imken.messagevault.mobile.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import timber.log.Timber

/**
 * 权限工具类
 * 
 * 提供权限检查、请求和处理的通用方法
 */
object PermissionUtils {
    
    // 应用所需的基本权限列表
    private val BASIC_PERMISSIONS = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    )
    
    // Android 11+上需要特殊处理的权限
    private val ANDROID_11_SPECIAL_PERMISSIONS = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG
    )
    
    /**
     * 检查所有必需的权限是否已授予
     * 
     * @param context 上下文
     * @return 如果所有权限都已授予则返回true，否则返回false
     */
    fun checkAllRequiredPermissions(context: Context): Boolean {
        return BASIC_PERMISSIONS.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 获取未授予的权限列表
     * 
     * @param context 上下文
     * @return 未授予的权限列表
     */
    fun getNotGrantedPermissions(context: Context): List<String> {
        return BASIC_PERMISSIONS.filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 检查当前应用是否为默认短信应用
     * 
     * @param context 上下文
     * @return 如果是默认短信应用则返回true，否则返回false
     */
    fun isDefaultSmsApp(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // 优先使用RoleManager（Android 10+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                    if (roleManager != null) {
                        val hasRole = roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
                        Timber.d("[Mobile] DEBUG [Permissions] RoleManager检查结果: $hasRole")
                        if (hasRole) {
                            return true
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Permissions] 检查RoleManager时出错")
                }
            }
            
            // 如果RoleManager检查未成功或不可用，使用传统方法
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            val isDefault = defaultSmsPackage == context.packageName
            
            Timber.d("[Mobile] DEBUG [Permissions] 默认短信应用检查: 当前=${context.packageName}, 系统默认=$defaultSmsPackage, 是默认=$isDefault")
            
            return isDefault
        }
        return true // 在较旧版本中不需要是默认短信应用
    }
    
    /**
     * 创建请求默认短信应用的Intent
     * 
     * @param context 上下文
     * @return Intent对象，如果无法创建则返回null
     */
    fun createDefaultSmsAppIntent(context: Context): Intent? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // 处理不同Android版本的请求方式
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                        if (roleManager != null) {
                            // 在Android 11+上使用RoleManager
                            return roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "[Mobile] ERROR [Permissions] 请求SMS角色失败: ${e.message}")
                    }
                }
                
                // 对于所有Android 4.4+版本的兼容方式
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                
                // 确保活动存在
                if (intent.resolveActivity(context.packageManager) != null) {
                    return intent
                } else {
                    Timber.w("[Mobile] WARN [Permissions] 无法找到处理默认短信应用请求的系统组件")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Permissions] 创建默认短信应用Intent失败: ${e.message}")
        }
        return null
    }
    
    /**
     * 打开应用设置页面
     * 
     * @param activity 活动
     */
    fun openAppSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:" + activity.packageName)
            activity.startActivity(intent)
            Timber.d("[Mobile] DEBUG [Permissions] 已打开应用设置页面")
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Permissions] 无法打开应用设置: ${e.message}")
        }
    }
    
    /**
     * 请求特殊权限（Android 11+）
     * 
     * 对于Android 11+上的特殊权限，需要引导用户到设置页面手动授予
     * 
     * @param activity 活动
     */
    fun requestSpecialPermissions(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                // 检查是否有任何特殊权限未授予
                val hasUngranted = ANDROID_11_SPECIAL_PERMISSIONS.any {
                    activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                }
                
                if (hasUngranted) {
                    openAppSettings(activity)
                    Timber.i("[Mobile] INFO [Permissions] 已引导用户到设置页面授予特殊权限")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Permissions] 请求特殊权限失败: ${e.message}")
            }
        }
    }
} 