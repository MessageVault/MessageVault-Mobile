package imken.messagevault.mobile.models

/**
 * 通话记录数据模型
 */
data class CallLogData(
    val id: Long,
    val number: String,
    val name: String?,
    val date: Long,
    val duration: Int,
    val type: Int
)
