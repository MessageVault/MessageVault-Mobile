package imken.messagevault.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import timber.log.Timber
import java.util.Date

/**
 * SMS广播接收器
 * 
 * 用于接收系统发送的短信广播，使应用能够作为默认短信应用
 * 注意：此接收器仅用于满足默认短信应用的要求，实际不处理短信
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 仅记录接收到短信的日志，不做实际处理
        // 正常短信应用会在这里解析短信并保存到数据库
        Timber.d("%tFT%<tT.%<tLZ [Mobile] DEBUG [SMS] 接收到短信广播; Context: ${intent.action}", Date())
        
        // 这里不做任何实际处理，因为我们的应用仅临时成为默认短信应用
        // 仅为了恢复备份时写入短信数据
    }
} 