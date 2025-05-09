package imken.messagevault.mobile

import android.app.Application
import android.util.Log
import timber.log.Timber

/**
 * 应用入口类
 * 
 * 负责应用级别的初始化工作
 */
class MessageVaultApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化日志系统
        initLogging()
        
        Timber.i("[Mobile] INFO [App] 应用启动; 版本=${BuildConfig.VERSION_NAME}")
    }
    
    /**
     * 初始化日志系统
     */
    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            // 调试模式：详细日志
            Timber.plant(object : Timber.DebugTree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // 确保所有日志也通过Android Log系统输出
                    Log.println(priority, "MessageVault", message)
                    super.log(priority, tag, message, t)
                }
            })
            
            Timber.d("[Mobile] DEBUG [App] 调试模式日志系统初始化完成")
        } else {
            // 发布模式：仅记录重要日志（INFO级别及以上）
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    if (priority >= Log.INFO) {
                        Log.println(priority, "MessageVault", message)
                    }
                }
            })
        }
    }
}
