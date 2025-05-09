package imken.messagevault.mobile.models

import androidx.annotation.Nullable

/**
 * 短信数据模型
 */
data class MessageData(
    val id: Long,
    val address: String,
    val body: String?, // 消息内容，可为空
    val date: Long,
    val type: Int,
    val readState: Int, // 已读状态
    val messageStatus: Int, // 消息状态
    val threadId: Long? // 会话ID，可为空
)
