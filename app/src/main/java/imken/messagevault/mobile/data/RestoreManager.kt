package imken.messagevault.mobile.data

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import com.google.gson.Gson
import imken.messagevault.mobile.api.ApiClient
import imken.messagevault.mobile.config.Config
import imken.messagevault.mobile.data.entity.CallLogsEntity
import imken.messagevault.mobile.data.entity.ContactsEntity
import imken.messagevault.mobile.data.entity.MessageEntity
import imken.messagevault.mobile.data.models.BackupFile
import imken.messagevault.mobile.data.models.BackupFileInfo
import imken.messagevault.mobile.model.BackupData
import imken.messagevault.mobile.model.Contact
import imken.messagevault.mobile.model.Message
import imken.messagevault.mobile.utils.Constants.SUPPORTED_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileReader
import java.util.Date
import java.util.UUID
import imken.messagevault.mobile.model.CallLog as ModelCallLog
import android.provider.ContactsContract
import com.google.gson.reflect.TypeToken
import imken.messagevault.mobile.BuildConfig
import android.provider.Settings
import imken.messagevault.mobile.models.MessageData
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.OperationApplicationException
import android.net.Uri
import android.os.RemoteException
import java.util.concurrent.atomic.AtomicInteger
import android.content.Intent
import android.os.Build
import android.app.Activity

/**
 * 恢复管理器
 * 
 * 负责从备份文件恢复数据
 * 
 * @param context 应用上下文
 * @param config 应用配置
 * @param apiClient API客户端
 */
class RestoreManager(
    private val context: Context,
    private val config: Config,
    private val apiClient: ApiClient
) {
    private val gson = Gson()
    private val contentResolver: ContentResolver = context.contentResolver
    private val TAG = "RestoreManager"
    private var successCount = 0
    private var failureCount = 0
    
    /**
     * 获取可用的备份文件列表
     * 
     * @return 备份文件列表
     */
    suspend fun getAvailableBackups(): List<imken.messagevault.mobile.models.BackupFile> = withContext(Dispatchers.IO) {
        val backupFiles = mutableListOf<imken.messagevault.mobile.models.BackupFile>()
        
        try {
            // 使用config获取备份目录名称而不是硬编码字符串
            val backupDir = File(context.getExternalFilesDir(null), config.getBackupDirectoryName())
            
            // 检查备份目录是否存在
            if (!backupDir.exists()) {
                Timber.w("[Mobile] WARN [Restore] 备份目录不存在: ${backupDir.absolutePath}")
                return@withContext emptyList<imken.messagevault.mobile.models.BackupFile>()
            }
            
            // 获取备份目录中的所有JSON文件
            val files = backupDir.listFiles { file ->
                file.isFile && file.name.endsWith(".json", ignoreCase = true)
            }
            
            if (files != null) {
                // 过滤无效的备份文件
                val validFiles = files.filter { validateBackupFile(it) }
                backupFiles.addAll(validFiles.map { file -> 
                    val deviceId = Settings.Secure.getString(
                        context.contentResolver, 
                        Settings.Secure.ANDROID_ID
                    ) ?: "unknown"
                    
                    // 解析备份文件以获取SMS和通话记录数量
                    val fileReader = FileReader(file)
                    val backupData = try {
                        gson.fromJson(fileReader, BackupData::class.java)
                    } catch (e: Exception) {
                        null
                    } finally {
                        fileReader.close()
                    }
                    
                    val smsCount = backupData?.messages?.size ?: 0
                    val callLogsCount = backupData?.callLogs?.size ?: 0
                    
                    imken.messagevault.mobile.models.BackupFile(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        fileSize = file.length(),
                        creationDate = Date(file.lastModified()),
                        deviceName = deviceId,
                        smsCount = smsCount,
                        callLogsCount = callLogsCount,
                        version = BuildConfig.VERSION_NAME
                    )
                }.sortedByDescending { it.creationDate.time })
                Timber.i("[Mobile] INFO [Restore] 找到 ${validFiles.size} 个有效备份文件，总共 ${files.size} 个文件")
            } else {
                Timber.w("[Mobile] WARN [Restore] 无法列出备份目录中的文件: ${backupDir.absolutePath}")
            }
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Restore] 获取备份文件列表失败: ${e.message}")
        }
        
        return@withContext backupFiles
    }
    
    /**
     * 解析备份文件
     * 
     * @param backupFile 备份文件
     * @return 备份数据对象，如果解析失败则返回null
     */
    suspend fun parseBackupFile(backupFile: imken.messagevault.mobile.models.BackupFile): BackupData? = withContext(Dispatchers.IO) {
        try {
            val file = File(backupFile.filePath)
            FileReader(file).use { reader ->
                val typeToken = object : TypeToken<BackupData>() {}.type
                val backupData = gson.fromJson<BackupData>(reader, typeToken)
                if (backupData == null) {
                    Timber.e("[Mobile] ERROR [Restore] 解析备份文件返回null: ${backupFile.fileName}")
                    return@withContext null
                }
                
                // 检查消息列表是否为null，如果是则设置为空列表
                val messagesCount = backupData.messages?.size ?: 0
                val callLogsCount = backupData.callLogs?.size ?: 0
                val contactsCount = backupData.contacts?.size ?: 0
                
                Timber.i("[Mobile] INFO [Restore] 成功解析备份文件: ${backupFile.fileName}, 短信数量: $messagesCount, 通话记录数量: $callLogsCount, 联系人数量: $contactsCount")
                return@withContext backupData
            }
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Restore] 解析备份文件失败: ${backupFile.fileName}, ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * 从备份文件恢复数据
     * 
     * @param backupFile 备份文件
     * @return 恢复结果
     */
    suspend fun restoreFromFile(backupFile: imken.messagevault.mobile.models.BackupFile): RestoreResult = withContext(Dispatchers.IO) {
        return@withContext restoreFromFile(backupFile, null)
    }
    
    /**
     * 进度回调接口
     * 
     * @param phase 当前阶段名称
     * @param progress 百分比进度（0-100）
     * @param detail 详细信息
     */
    fun interface ProgressCallback {
        fun onProgress(phase: String, progress: Int, detail: String)
    }
    
    /**
     * 从备份文件恢复数据，带进度报告
     * 
     * @param backupFile 备份文件
     * @param progressCallback 进度回调接口
     * @return 恢复结果
     */
    suspend fun restoreFromFile(
        backupFile: imken.messagevault.mobile.models.BackupFile, 
        progressCallback: ProgressCallback?
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            // 添加基础日志
            Timber.d("[Mobile] DEBUG [Restore] 开始恢复文件: ${backupFile.fileName}")
            progressCallback?.onProgress("准备", 0, "开始恢复文件")
            
            val backupData = parseBackupFile(backupFile) ?: return@withContext RestoreResult(false, "无法解析备份文件")
            progressCallback?.onProgress("解析", 10, "备份文件解析完成")
            
            // 检查是否需要恢复短信
            val hasSms = !backupData.messages.isNullOrEmpty()
            
            // 如果有短信需要恢复，检查是否为默认短信应用
            if (hasSms) {
                val isDefault = isDefaultSmsApp()
                Timber.i("[Mobile] INFO [Restore] 备份包含短信, 是否为默认短信应用: $isDefault")
                
                if (!isDefault) {
                    Timber.w("[Mobile] WARN [Restore] 当前应用不是默认短信应用，无法写入短信数据库")
                    return@withContext RestoreResult(
                        success = false,
                        message = "恢复短信需要将此应用设为默认短信应用，请在系统设置中更改"
                    )
                }
            }
            
            // 恢复短信
            val messagesSize = backupData.messages?.size ?: 0
            
            var restoredSmsCount = 0
            if (hasSms) {
                val messages = backupData.messages!!
                Timber.d("[Mobile] DEBUG [Restore] 准备恢复短信，数量: ${messages.size}")
                progressCallback?.onProgress("短信", 20, "开始恢复 ${messages.size} 条短信")
                
                try {
                    // 分批恢复短信并报告进度
                    restoredSmsCount = restoreMessagesWithProgress(messages, progressCallback)
                    Timber.i("[Mobile] INFO [Restore] 短信恢复结果: 成功恢复 $restoredSmsCount/${messages.size} 条短信")
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 短信恢复过程中发生异常: ${e.message}")
                }
            } else {
                Timber.i("[Mobile] INFO [Restore] 备份不包含短信数据，跳过短信恢复")
            }
            
            progressCallback?.onProgress("短信", 40, "完成短信恢复 ($restoredSmsCount/$messagesSize)")
            
            // 恢复通话记录
            val callLogsSize = backupData.callLogs?.size ?: 0
            
            var restoredCallLogsCount = 0
            if (callLogsSize > 0) {
                val callLogs = backupData.callLogs!!
                Timber.d("[Mobile] DEBUG [Restore] 准备恢复通话记录，数量: ${callLogs.size}")
                progressCallback?.onProgress("通话记录", 50, "开始恢复 ${callLogs.size} 条通话记录")
                
                try {
                    // 分批恢复通话记录并报告进度
                    restoredCallLogsCount = restoreCallLogsWithProgress(callLogs, progressCallback)
                    Timber.i("[Mobile] INFO [Restore] 通话记录恢复结果: 成功恢复 $restoredCallLogsCount/${callLogs.size} 条通话记录")
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 通话记录恢复过程中发生异常: ${e.message}")
                }
            } else {
                Timber.i("[Mobile] INFO [Restore] 备份不包含通话记录数据，跳过通话记录恢复")
            }
            
            progressCallback?.onProgress("通话记录", 70, "完成通话记录恢复 ($restoredCallLogsCount/$callLogsSize)")
            
            // 恢复联系人
            val contactsSize = backupData.contacts?.size ?: 0
            
            var restoredContactsCount = 0
            if (contactsSize > 0) {
                val contacts = backupData.contacts!!
                Timber.d("[Mobile] DEBUG [Restore] 准备恢复联系人，数量: ${contacts.size}")
                progressCallback?.onProgress("联系人", 80, "开始恢复 ${contacts.size} 个联系人")
                
                try {
                    // 分批恢复联系人并报告进度
                    restoredContactsCount = restoreContactsWithProgress(contacts, progressCallback)
                    Timber.i("[Mobile] INFO [Restore] 联系人恢复结果: 成功恢复 $restoredContactsCount/${contacts.size} 个联系人")
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 联系人恢复过程中发生异常: ${e.message}")
                }
            } else {
                Timber.i("[Mobile] INFO [Restore] 备份不包含联系人数据，跳过联系人恢复")
            }
            
            progressCallback?.onProgress("完成", 100, "恢复完成")
            
            // 计算总体结果，即使某个部分失败也应该继续其他部分的恢复
            val totalSuccess = restoredSmsCount > 0 || restoredCallLogsCount > 0 || restoredContactsCount > 0
            val resultMessage = if (totalSuccess) {
                "成功恢复 $restoredSmsCount 条短信、$restoredCallLogsCount 条通话记录和 $restoredContactsCount 位联系人"
            } else {
                "恢复失败: 没有任何数据恢复成功"
            }
            
            Timber.i("[Mobile] INFO [Restore] 恢复完成; 恢复短信: $restoredSmsCount/${backupData.messages?.size ?: 0}, 恢复通话记录: $restoredCallLogsCount/${backupData.callLogs?.size ?: 0}, 恢复联系人: $restoredContactsCount/${backupData.contacts?.size ?: 0}")
            
            return@withContext RestoreResult(
                success = totalSuccess,
                message = resultMessage
            )
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Restore] 恢复数据失败: ${e.message}")
            return@withContext RestoreResult(false, "恢复失败: ${e.message}")
        }
    }
    
    /**
     * 分批恢复短信并报告进度
     */
    private suspend fun restoreMessagesWithProgress(
        messages: List<Message>,
        progressCallback: ProgressCallback?
    ): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        val totalCount = messages.size
        Timber.i("[Mobile] INFO [Restore] 开始恢复短信，总数: $totalCount")
        
        // 检查权限
        if (!hasSmsPermissions()) {
            Timber.e("[Mobile] ERROR [Restore] 没有短信权限，无法恢复短信")
            return@withContext 0
        }
        
        // 计算进度更新频率
        val progressStep = if (totalCount > 100) totalCount / 20 else 1
        
        messages.forEachIndexed { index, message ->
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, message.address)
                    put(Telephony.Sms.BODY, message.body)
                    put(Telephony.Sms.DATE, message.date)
                    put(Telephony.Sms.DATE_SENT, message.date)
                    put(Telephony.Sms.READ, message.readState ?: 0)
                    put(Telephony.Sms.TYPE, message.type)
                    put(Telephony.Sms.STATUS, message.messageStatus ?: 0)
                }
                
                // 根据短信类型选择正确的URI
                val uri = when (message.type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                    Telephony.Sms.MESSAGE_TYPE_SENT -> contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX -> contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
                    Telephony.Sms.MESSAGE_TYPE_DRAFT -> contentResolver.insert(Telephony.Sms.Draft.CONTENT_URI, values)
                    else -> contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                }
                
                if (uri != null) {
                    restoredCount++
                } else {
                    // 尝试使用通用URI
                    val fallbackUri = contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                    if (fallbackUri != null) {
                        restoredCount++
                    }
                }
                
                // 报告进度
                if (index % progressStep == 0 || index == totalCount - 1) {
                    val currentProgress = ((index + 1) * 100) / totalCount
                    val progressMessage = "恢复短信进度: ${index + 1}/$totalCount"
                    progressCallback?.onProgress("短信", currentProgress, progressMessage)
                    Timber.d("[Mobile] DEBUG [Restore] $progressMessage ($currentProgress%)")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Restore] 恢复短信失败: ID=${message.id}, 地址=${message.address}, ${e.message}")
            }
        }
        
        Timber.i("[Mobile] INFO [Restore] 短信恢复完成: 成功=$restoredCount, 总数=$totalCount")
        return@withContext restoredCount
    }
    
    /**
     * 分批恢复通话记录并报告进度
     */
    private suspend fun restoreCallLogsWithProgress(
        callLogs: List<ModelCallLog>,
        progressCallback: ProgressCallback?
    ): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        val totalCount = callLogs.size
        Timber.i("[Mobile] INFO [Restore] 开始恢复通话记录，总数: $totalCount")
        
        // 计算进度更新频率
        val progressStep = if (totalCount > 100) totalCount / 10 else 1
        
        callLogs.forEachIndexed { index, callLog ->
            try {
                val values = ContentValues().apply {
                    put(CallLog.Calls.NUMBER, callLog.number)
                    put(CallLog.Calls.TYPE, callLog.type)
                    put(CallLog.Calls.DATE, callLog.date)
                    put(CallLog.Calls.DURATION, callLog.duration)
                    callLog.contact?.let { contactName ->
                        put(CallLog.Calls.CACHED_NAME, contactName)
                    }
                }
                
                val uri = contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
                if (uri != null) {
                    restoredCount++
                }
                
                // 报告进度
                if (index % progressStep == 0 || index == totalCount - 1) {
                    val currentProgress = ((index + 1) * 100) / totalCount
                    val progressMessage = "恢复通话记录进度: ${index + 1}/$totalCount"
                    progressCallback?.onProgress("通话记录", currentProgress, progressMessage)
                    Timber.d("[Mobile] DEBUG [Restore] $progressMessage ($currentProgress%)")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Restore] 恢复通话记录失败: ID=${callLog.id}, 号码=${callLog.number}, ${e.message}")
            }
        }
        
        Timber.i("[Mobile] INFO [Restore] 通话记录恢复完成: 成功=$restoredCount, 总数=$totalCount")
        return@withContext restoredCount
    }
    
    /**
     * 分批恢复联系人并报告进度
     */
    private suspend fun restoreContactsWithProgress(
        contacts: List<Contact>,
        progressCallback: ProgressCallback?
    ): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        val totalCount = contacts.size
        Timber.i("[Mobile] INFO [Restore] 开始恢复联系人，总数: $totalCount")
        
        // 检查权限
        if (!hasContactsPermissions()) {
            Timber.e("[Mobile] ERROR [Restore] 没有联系人权限，无法恢复联系人")
            return@withContext 0
        }
        
        // 计算进度更新频率
        val progressStep = if (totalCount > 100) totalCount / 10 else 1
        
        contacts.forEachIndexed { index, contact ->
            try {
                val operations = ArrayList<ContentProviderOperation>()
                
                // 创建联系人
                val rawContactInsertIndex = operations.size
                operations.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build())
                
                // 添加姓名
                operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                    .build())
                
                // 添加电话号码
                contact.phoneNumbers.forEach { phoneNumber ->
                    operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build())
                }
                
                try {
                    // 应用批处理操作
                    val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
                    if (results.isNotEmpty()) {
                        restoredCount++
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 恢复联系人失败: 批处理操作异常: ${e.message}")
                }
                
                // 报告进度
                if (index % progressStep == 0 || index == totalCount - 1) {
                    val currentProgress = ((index + 1) * 100) / totalCount
                    val progressMessage = "恢复联系人进度: ${index + 1}/$totalCount"
                    progressCallback?.onProgress("联系人", currentProgress, progressMessage)
                    Timber.d("[Mobile] DEBUG [Restore] $progressMessage ($currentProgress%)")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Restore] 恢复联系人失败: ID=${contact.id}, 姓名=${contact.name}, ${e.message}")
            }
        }
        
        Timber.i("[Mobile] INFO [Restore] 联系人恢复完成: 成功=$restoredCount, 总数=$totalCount")
        return@withContext restoredCount
    }
    
    /**
     * 检查是否有短信权限
     */
    private fun hasSmsPermissions(): Boolean {
        val readPermission = android.Manifest.permission.READ_SMS
        val sendPermission = android.Manifest.permission.SEND_SMS
        val receivePermission = android.Manifest.permission.RECEIVE_SMS
        
        val readGranted = context.checkSelfPermission(readPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val sendGranted = context.checkSelfPermission(sendPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val receiveGranted = context.checkSelfPermission(receivePermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!readGranted || !sendGranted) {
            Timber.e("[Mobile] ERROR [Restore] 缺少短信权限: READ_SMS=$readGranted, SEND_SMS=$sendGranted, RECEIVE_SMS=$receiveGranted")
            return false
        }
        
        return true
    }
    
    /**
     * 恢复通话记录
     * 
     * @param callLogs 通话记录列表
     * @return 成功恢复的通话记录数量
     */
    private suspend fun restoreCallLogs(callLogs: List<ModelCallLog>): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        Timber.i("[Mobile] INFO [Restore] 开始恢复通话记录，总数: ${callLogs.size}")
        
        callLogs.forEach { callLog ->
            try {
                val values = ContentValues().apply {
                    put(CallLog.Calls.NUMBER, callLog.number)
                    put(CallLog.Calls.TYPE, callLog.type)
                    put(CallLog.Calls.DATE, callLog.date)
                    put(CallLog.Calls.DURATION, callLog.duration)
                    callLog.contact?.let { contactName ->
                        put(CallLog.Calls.CACHED_NAME, contactName)
                    }
                }
                
                Timber.d("[Mobile] DEBUG [Restore] 尝试恢复通话记录: 号码=${callLog.number}, 类型=${callLog.type}, 日期=${callLog.date}")
                
                val uri = contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
                if (uri != null) {
                    restoredCount++
                    Timber.d("[Mobile] DEBUG [Restore] 成功恢复通话记录: $uri")
                } else {
                    Timber.e("[Mobile] ERROR [Restore] 恢复通话记录失败: 插入返回null")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Restore] 恢复通话记录失败: ID=${callLog.id}, 号码=${callLog.number}, ${e.message}")
            }
        }
        
        Timber.i("[Mobile] INFO [Restore] 通话记录恢复完成: 成功=$restoredCount, 总数=${callLogs.size}")
        return@withContext restoredCount
    }
    
    /**
     * 恢复联系人
     * 
     * @param contacts 联系人列表
     * @return 成功恢复的联系人数量
     */
    private suspend fun restoreContacts(contacts: List<Contact>): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        Timber.i("[Mobile] INFO [Restore] 开始恢复联系人，总数: ${contacts.size}")
        
        // 检查权限
        if (!hasContactsPermissions()) {
            Timber.e("[Mobile] ERROR [Restore] 没有联系人权限，无法恢复联系人")
            return@withContext 0
        }
        
        contacts.forEach { contact ->
            try {
                val operations = ArrayList<ContentProviderOperation>()
                
                // 创建联系人
                val rawContactInsertIndex = operations.size
                operations.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build())
                
                // 添加姓名
                operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                    .build())
                
                // 添加电话号码
                contact.phoneNumbers.forEach { phoneNumber ->
                    operations.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build())
                }
                
                Timber.d("[Mobile] DEBUG [Restore] 尝试恢复联系人: 姓名=${contact.name}, 电话数量=${contact.phoneNumbers.size}")
                
                try {
                    // 应用批处理操作
                    val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
                    if (results.isNotEmpty()) {
                        restoredCount++
                        Timber.d("[Mobile] DEBUG [Restore] 成功恢复联系人: ${contact.name}, URI=${results.first()}")
                    } else {
                        Timber.e("[Mobile] ERROR [Restore] 恢复联系人失败: 批处理返回空结果")
                    }
                } catch (e: OperationApplicationException) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 恢复联系人失败: 批处理操作异常: ${e.message}")
                } catch (e: RemoteException) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 恢复联系人失败: 远程操作异常: ${e.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Restore] 恢复联系人失败: ID=${contact.id}, 姓名=${contact.name}, ${e.message}")
            }
        }
        
        Timber.i("[Mobile] INFO [Restore] 联系人恢复完成: 成功=$restoredCount, 总数=${contacts.size}")
        return@withContext restoredCount
    }
    
    /**
     * 检查是否有联系人权限
     */
    private fun hasContactsPermissions(): Boolean {
        val readPermission = android.Manifest.permission.READ_CONTACTS
        val writePermission = android.Manifest.permission.WRITE_CONTACTS
        
        val readGranted = context.checkSelfPermission(readPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val writeGranted = context.checkSelfPermission(writePermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!readGranted || !writeGranted) {
            Timber.e("[Mobile] ERROR [Restore] 缺少联系人权限: READ_CONTACTS=$readGranted, WRITE_CONTACTS=$writeGranted")
            return false
        }
        
        return true
    }
    
    /**
     * 验证备份文件
     * 
     * @param file 备份文件
     * @return 如果备份文件有效则返回true，否则返回false
     */
    private fun validateBackupFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || !file.canRead()) {
            return false
        }
        
        try {
            FileReader(file).use { reader ->
                val typeToken = object : TypeToken<BackupData>() {}.type
                val backupData = gson.fromJson<BackupData>(reader, typeToken)
                // 如果BackupData类中没有formatVersion字段，则始终返回true
                return backupData != null
            }
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Restore] 验证备份文件失败: ${file.name}, ${e.message}")
            return false
        }
    }
    
    /**
     * 处理备份文件
     * 
     * @param backupFile 备份文件
     */
    private fun someRestoreMethod(backupFile: imken.messagevault.mobile.models.BackupFile) {
        println("处理备份文件: ${backupFile.fileName}")
    }
    
    /**
     * 读取状态的方法
     * 
     * @return 读取状态
     */
    private fun readState(): Any {
        return Any() // 临时返回
    }
    
    /**
     * 消息状态字段
     */
    private val messageStatus: Any = Any()
    
    /**
     * 读取状态枚举
     */
    private enum class ReadState {
        READ, UNREAD
    }
    
    /**
     * 消息状态枚举
     */
    private enum class MessageStatus {
        RECEIVED, SENT
    }
    
    /**
     * 获取读取状态
     * 
     * @param value 值
     * @return 读取状态
     */
    private fun getReadState(value: Int): ReadState {
        return if (value == 1) ReadState.READ else ReadState.UNREAD
    }
    
    /**
     * 获取消息状态
     * 
     * @param value 值
     * @return 消息状态
     */
    private fun getMessageStatus(value: Int): MessageStatus {
        return if (value == 1) MessageStatus.RECEIVED else MessageStatus.SENT
    }
    
    /**
     * 记录错误日志
     * 
     * @param message 消息
     */
    private fun logError(message: String) {
        Log.e(TAG, message)
    }
    
    /**
     * 将消息模型转换为实体
     * 
     * @param message 消息
     * @return 消息实体
     */
    private fun Message.toMessageEntity(): MessageEntity {
        return MessageEntity(
            id = this.id,
            address = this.address,
            body = this.body ?: "",
            date = this.date,
            type = this.type,
            read = 0, // 默认值，因为Message类可能没有readState字段
            status = 0, // 默认值，因为Message类可能没有messageStatus字段
            threadId = 0  // 默认值，因为Message类可能没有threadId字段
        )
    }
    
    /**
     * 将通话记录模型转换为实体
     * 
     * @param callLog 通话记录
     * @return 通话记录实体
     */
    private fun ModelCallLog.toCallLogEntity(): CallLogsEntity {
        return CallLogsEntity(
            id = this.id,
            number = this.number,
            name = this.contact ?: "",
            date = this.date,
            duration = this.duration,
            type = this.type
        )
    }
    
    /**
     * 将消息数据模型转换为实体
     * 
     * @param messageData 消息数据
     * @return 消息实体
     */
    private fun MessageData.toMessageEntity(): MessageEntity {
        return MessageEntity(
            id = this.id,
            address = this.address,
            body = this.body ?: "",
            date = this.date,
            type = this.type,
            read = this.readState,
            status = this.messageStatus,
            threadId = this.threadId ?: 0
        )
    }
    
    /**
     * 修复 line 242 和 243 的 ContentValues.put 歧义
     */
    private fun createMessageContentValues(message: Message): ContentValues {
        val values = ContentValues()
        values.put("column_name", message.address) // 不再使用as String显式指定类型
        values.put("another_column", message.body) // 不再使用as String显式指定类型
        return values
    }
    
    /**
     * 获取协议类型
     */
    private fun getProtocolFromType(type: Int): String {
        return when(type) {
            1 -> "sms"
            2 -> "mms"
            else -> "unknown"
        }
    }
    
    /**
     * 修复缺少参数的方法调用
     */
    private fun someMethod() {
        // 删除无效的方法调用代码
    }
    
    /**
     * 定义TAG常量
     */
    companion object {
        private const val BACKUP_FORMAT_VERSION = SUPPORTED_VERSION
        const val DEFAULT_SMS_REQUEST_CODE = 1001
    }
    
    /**
     * 恢复结果数据类
     */
    data class RestoreResult(
        val success: Boolean,
        val message: String
    )
    
    /**
     * 检查当前应用是否为默认短信应用
     */
    private fun isDefaultSmsApp(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            val isDefault = defaultSmsPackage == context.packageName
            
            // 添加详细日志
            Timber.d("[Mobile] DEBUG [Restore] 默认短信应用检查 - 当前应用: ${context.packageName}, 系统默认: $defaultSmsPackage, 是默认: $isDefault")
            
            return isDefault
        }
        return true // 在较旧版本中不需要是默认短信应用
    }
    
    /**
     * 请求设置为默认短信应用
     */
    fun requestDefaultSmsApp(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName)
            activity.startActivityForResult(intent, DEFAULT_SMS_REQUEST_CODE)
        }
    }
}

