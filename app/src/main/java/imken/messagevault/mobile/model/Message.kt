package imken.messagevault.mobile.model

/**
 * 消息模型类
 */
data class Message(
    val id: Long = 0,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val readState: Int? = 0,
    val messageStatus: Int? = 0,
    val threadId: Long = 0
)
