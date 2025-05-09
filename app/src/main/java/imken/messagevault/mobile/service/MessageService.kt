package imken.messagevault.mobile.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import timber.log.Timber
import java.util.Date

/**
 * 消息服务
 * 
 * 此服务用于处理短信相关的后台任务，是构建默认短信应用的必要组件
 * 注意：此服务仅用于满足默认短信应用的要求，不实际处理短信
 */
class MessageService : Service() {
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("%tFT%<tT.%<tLZ [Mobile] DEBUG [SMS] 短信服务已创建", Date())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 记录收到的服务启动请求，但不做实际处理
        intent?.let {
            val action = it.action
            
            Timber.d("%tFT%<tT.%<tLZ [Mobile] DEBUG [SMS] 短信服务收到请求; Action: $action", Date())
        }
        
        // 立即停止服务，因为我们不实际处理短信
        stopSelf(startId)
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("%tFT%<tT.%<tLZ [Mobile] DEBUG [SMS] 短信服务已销毁", Date())
    }
} 