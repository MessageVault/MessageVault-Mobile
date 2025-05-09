package imken.messagevault.mobile

import android.app.Application
import android.os.Environment
import imken.messagevault.mobile.config.Config
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * MessageVault应用类
 * 
 * 此类是整个应用的入口点，负责初始化关键组件和服务。
 * 当前主要职责是设置日志系统，遵循NOTICE.md中的日志格式要求。
 * 
 * 日志格式：
 * 2025-04-16T12:34:56Z [Mobile] ERROR [Type] Message; Context: Details with [CRITICAL] for key details
 */
class MessageVaultApp : Application() {

    private lateinit var config: Config

    override fun onCreate() {
        super.onCreate()
        
        // 初始化日志系统
        initializeLogging()
        
        // 初始化配置
        config = Config.getInstance(this)
        
        // 应用语言设置
        config.applyLanguage(this)
        Timber.i("[Mobile] INFO [Language] Initialized with language: ${config.getLanguage()}; Context: Application startup")
    }

    /**
     * 初始化日志系统
     * 
     * 配置Timber库进行日志记录，包括：
     * 1. 开发环境下的调试日志树
     * 2. 生产环境下的文件日志树（保存到logs/目录）
     * 
     * 可扩展点：
     * - 添加远程日志收集服务集成
     * - 根据不同构建类型配置不同的日志级别
     * - 添加崩溃报告功能
     */
    private fun initializeLogging() {
        // 在调试模式下植入调试树
        if ( BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // 创建文件日志树，将日志写入文件
        Timber.plant(FileLoggingTree(this))
        
        Timber.i("[Mobile] INFO [Init] 应用启动; Context: 初始化环境 API ${android.os.Build.VERSION.SDK_INT}")
    }

    /**
     * 文件日志树实现
     * 
     * 根据NOTICE.md要求格式化日志并将其写入文件系统
     * 日志文件保存在应用私有目录下的logs/文件夹
     */
    private inner class FileLoggingTree(private val application: Application) : Timber.Tree() {
        
        private val logDir: File by lazy {
            val dir = File(application.getExternalFilesDir(null), "logs")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            dir
        }
        
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        
        private val logFile: File
            get() {
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                return File(logDir, "messagevault-$dateStr.log")
            }
    
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            try {
                val priorityStr = when (priority) {
                    android.util.Log.VERBOSE -> "VERBOSE"
                    android.util.Log.DEBUG -> "DEBUG"
                    android.util.Log.INFO -> "INFO"
                    android.util.Log.WARN -> "WARN"
                    android.util.Log.ERROR -> "ERROR"
                    android.util.Log.ASSERT -> "FATAL"
                    else -> "UNKNOWN"
                }
                
                val timestamp = dateFormat.format(Date())
                val logMessage = "$timestamp [Mobile] $priorityStr $message"
                
                // 确保日志目录存在
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                
                // 追加写入日志文件
                logFile.appendText("$logMessage\n")
                
                // 如果有异常，也记录异常信息
                t?.let {
                    logFile.appendText("${it.message}\n")
                    it.stackTrace.forEach { element ->
                        logFile.appendText("    at $element\n")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MessageVaultApp", "写入日志文件失败", e)
            }
        }
    }
} 