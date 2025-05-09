package imken.messagevault.mobile.data.api

import imken.messagevault.mobile.model.BackupData
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * 备份API接口
 * 
 * 定义用于备份数据到服务器的API端点
 */
interface BackupApi {
    /**
     * 上传备份数据到服务器
     * 
     * @param authToken 认证令牌
     * @param backupData 要上传的备份数据
     * @return Call对象，包含API响应
     */
    @POST("backup")
    fun uploadBackup(
        @Header("Authorization") authToken: String,
        @Body backupData: BackupData
    ): Call<BackupResponse>
}

/**
 * 备份响应数据模型
 * 
 * 表示服务器对备份请求的响应
 */
data class BackupResponse(
    /**
     * 操作是否成功
     */
    val success: Boolean,
    
    /**
     * 备份ID，由服务器生成
     */
    val backupId: String?,
    
    /**
     * 错误消息（如果有）
     */
    val error: String?,
    
    /**
     * 服务器时间戳
     */
    val timestamp: Long
) 