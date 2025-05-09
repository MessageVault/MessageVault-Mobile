package imken.messagevault.mobile.model

/**
 * 表示文件上传操作的结果
 */
data class UploadResult(
    val success: Boolean,
    val fileId: String? = null,
    val errorMessage: String? = null
)
