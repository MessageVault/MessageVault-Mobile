package imken.messagevault.mobile.utils

import android.content.Context
import android.content.pm.PackageManager

/**
 * 应用相关的工具类
 */
object AppUtils {
    
    /**
     * 获取应用版本名称
     */
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "未知版本"
        }
    }
}
