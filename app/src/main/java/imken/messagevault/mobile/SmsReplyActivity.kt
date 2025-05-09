package imken.messagevault.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import timber.log.Timber
import java.util.Date

/**
 * 短信回复活动
 * 
 * 处理发送短信的意图，使应用能够作为默认短信应用
 * 注意：此活动仅用于满足默认短信应用的要求，实际不实现短信发送功能
 */
class SmsReplyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 记录接收到的短信发送意图
        val action = intent.action
        val data = intent.dataString
        Timber.d("%tFT%<tT.%<tLZ [Mobile] DEBUG [SMS] 收到短信发送意图; Action: $action, Data: $data", Date())
        
        // 设置简单的UI，提示用户此应用不提供短信发送功能
        setContent {
            MaterialTheme {
                SmsReplyScreen()
            }
        }
    }
}

/**
 * 短信回复界面
 * 
 * 显示简单的提示信息，说明此应用不提供短信发送功能
 */
@Composable
fun SmsReplyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "信驿云储不提供短信发送功能",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Text(
            text = "此应用仅用于备份和恢复短信，不支持发送短信",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
} 