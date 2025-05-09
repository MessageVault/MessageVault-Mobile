package imken.messagevault.mobile

import android.app.Application
import android.util.Log
import timber.log.Timber

class MessageVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashReportingTree())
        }
    }
    
    // 为生产环境定制的Timber树
    private class CrashReportingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG) {
                return
            }
            
            // 可以在这里添加崩溃报告逻辑
            if (priority >= Log.WARN) {
                // 例如: FirebaseCrashlytics.getInstance().log(message)
                
                if (t != null && priority >= Log.ERROR) {
                    // 例如: FirebaseCrashlytics.getInstance().recordException(t)
                }
            }
            
            // 可以选择在生产环境中保留一些关键日志
            Log.println(priority, "[Mobile]", message)
        }
    }
}
