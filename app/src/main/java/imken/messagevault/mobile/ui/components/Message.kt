package imken.messagevault.mobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 消息项组件
 * 
 * @param message 消息内容
 * @param timestamp 时间戳
 * @param isOutgoing 是否是发出的消息
 * @param status 消息状态（可选）
 * @param modifier 修饰符
 */
@Composable
fun MessageItem(
    message: String,
    timestamp: Long,
    isOutgoing: Boolean,
    status: MessageStatus = MessageStatus.DELIVERED,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formattedTime = dateFormat.format(Date(timestamp))
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(
                topStart = if (isOutgoing) 8.dp else 0.dp,
                topEnd = if (isOutgoing) 0.dp else 8.dp,
                bottomStart = 8.dp,
                bottomEnd = 8.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isOutgoing) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOutgoing) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOutgoing) 
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) 
                        else 
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    
                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        when (status) {
                            MessageStatus.SENDING -> Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = "发送中",
                                modifier = Modifier.padding(start = 4.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            MessageStatus.SENT -> Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = "已发送",
                                modifier = Modifier.padding(start = 4.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            MessageStatus.DELIVERED -> Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "已送达",
                                modifier = Modifier.padding(start = 4.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            MessageStatus.READ -> Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "已读",
                                modifier = Modifier.padding(start = 4.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 系统消息组件
 * 
 * @param message 消息内容
 * @param modifier 修饰符
 */
@Composable
fun SystemMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * 消息状态枚举
 */
enum class MessageStatus {
    SENDING,    // 发送中
    SENT,       // 已发送
    DELIVERED,  // 已送达
    READ        // 已读
} 