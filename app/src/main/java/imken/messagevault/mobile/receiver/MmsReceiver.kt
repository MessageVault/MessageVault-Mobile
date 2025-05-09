package imken.messagevault.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber
import java.util.Date

/**
 * MMS广播接收器
 * 
 * 用于接收系统发送的彩信WAP PUSH广播，使应用能够作为默认短信应用
 * 注意：此接收器仅用于满足默认短信应用的要求，实际不处理彩信
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 仅记录接收到彩信的日志，不做实际处理
        Timber.d("%tFT%<tT.%<tLZ [Mobile] DEBUG [MMS] 接收到彩信广播; Context: ${intent.action}", Date())
        
        // 这里不做任何实际处理，因为我们的应用仅临时成为默认短信应用
        // 仅为了恢复备份时写入短信数据
    }
} 