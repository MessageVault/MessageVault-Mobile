package imken.messagevault.mobile.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import timber.log.Timber
import java.util.Date

/**
 * 无界面短信发送服务
 * 
 * 此服务用于处理短信发送请求，是构建默认短信应用的必要组件
 * 注意：此服务仅用于满足默认短信应用的要求，不实际发送短信
 */
class HeadlessSmsSendService : Service() {
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 记录收到的短信发送请求，但不实际发送
        intent?.let {
            val action = it.action
            val destination = it.dataString
            
            Timber.d("%tFT%<tT.%<tLZ [Mobile] DEBUG [SMS] 收到短信发送请求; Action: $action, Destination: $destination", Date())
        }
        
        // 立即停止服务，因为我们不实际发送短信
        stopSelf(startId)
        return START_NOT_STICKY
    }
} 