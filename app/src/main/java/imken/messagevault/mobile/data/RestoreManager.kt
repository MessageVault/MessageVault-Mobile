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
import kotlinx.coroutines.delay

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
                // 添加日志用于调试解析过程
                val fileContent = file.readText()
                val containsCallLogs = fileContent.contains("\"callLogs\"") || fileContent.contains("\"call_logs\"")
                val containsMessages = fileContent.contains("\"messages\"") || fileContent.contains("\"sms\"")
                val containsContacts = fileContent.contains("\"contacts\"")
                
                Timber.d("[Mobile] DEBUG [Restore] 备份文件内容分析: 包含通话记录=$containsCallLogs, 包含短信=$containsMessages, 包含联系人=$containsContacts")
                
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
                
                // 额外日志用于调试
                if (containsCallLogs && callLogsCount == 0) {
                    Timber.w("[Mobile] WARN [Restore] 文件中包含通话记录字段但解析后为空，可能是字段名不匹配")
                }
                
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
     */
    interface ProgressCallback {
        fun onStart(operation: String)
        fun onProgressUpdate(operation: String, progress: Int)
        fun onProgressUpdate(operation: String, progress: Int, message: String)
        fun onComplete(success: Boolean, message: String = "")
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
            progressCallback?.onProgressUpdate("准备", 0, "开始恢复文件")
            
            val backupData = parseBackupFile(backupFile) ?: return@withContext RestoreResult(false, "无法解析备份文件")
            progressCallback?.onProgressUpdate("解析", 10, "备份文件解析完成")
            
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
                progressCallback?.onProgressUpdate("短信", 20, "开始恢复 ${messages.size} 条短信")
                
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
            
            progressCallback?.onProgressUpdate("短信", 40, "完成短信恢复 ($restoredSmsCount/$messagesSize)")
            
            // 恢复通话记录
            val hasCallLogs = !backupData.callLogs.isNullOrEmpty()
            val callLogsSize = backupData.callLogs?.size ?: 0
            
            Timber.d("[Mobile] DEBUG [Restore] 检测到通话记录: $hasCallLogs, 数量: $callLogsSize")
            
            var restoredCallLogsCount = 0
            if (hasCallLogs) {
                val callLogs = backupData.callLogs!!
                Timber.d("[Mobile] DEBUG [Restore] 准备恢复通话记录，数量: ${callLogs.size}")
                progressCallback?.onProgressUpdate("通话记录", 50, "开始恢复 ${callLogs.size} 条通话记录")
                
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
            
            progressCallback?.onProgressUpdate("通话记录", 70, "完成通话记录恢复 ($restoredCallLogsCount/$callLogsSize)")
            
            // 恢复联系人
            val contactsSize = backupData.contacts?.size ?: 0
            
            var restoredContactsCount = 0
            if (contactsSize > 0) {
                val contacts = backupData.contacts!!
                Timber.d("[Mobile] DEBUG [Restore] 准备恢复联系人，数量: ${contacts.size}")
                progressCallback?.onProgressUpdate("联系人", 80, "开始恢复 ${contacts.size} 个联系人")
                
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
            
            progressCallback?.onProgressUpdate("完成", 100, "恢复完成")
            
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
        
        // 诊断并修复备份数据中的问题
        val fixedMessages = validateAndFixMessageAddresses(messages)
        
        // 按地址分组消息，确保相同联系人的消息在同一会话中
        val messagesByAddress = fixedMessages.groupBy { it.address }
        
        // 维护地址到threadId的映射
        val addressToThreadId = mutableMapOf<String, Long>()
        
        // 按批次处理大量短信，避免内存压力
        var processedCount = 0
        
        // 先处理每个联系人的第一条消息，获取系统分配的threadId
        for ((address, addressMessages) in messagesByAddress) {
            if (address.isNullOrBlank()) continue
            
            try {
                // 先插入第一条消息，获取threadId
                val firstMessage = addressMessages.first()
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, firstMessage.body ?: "")
                    put(Telephony.Sms.DATE, firstMessage.date)
                    put(Telephony.Sms.DATE_SENT, firstMessage.date)
                    put(Telephony.Sms.READ, firstMessage.readState ?: 0)
                    put(Telephony.Sms.TYPE, firstMessage.type)
                    put(Telephony.Sms.STATUS, firstMessage.messageStatus ?: 0)
                }
                
                // 根据短信类型选择正确的URI
                val uri = when (firstMessage.type) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                    Telephony.Sms.MESSAGE_TYPE_SENT -> contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX -> contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
                    Telephony.Sms.MESSAGE_TYPE_DRAFT -> contentResolver.insert(Telephony.Sms.Draft.CONTENT_URI, values)
                    else -> contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                }
                
                if (uri != null) {
                    restoredCount++
                    processedCount++
                    
                    // 查询刚插入消息的threadId
                    val cursor = contentResolver.query(
                        uri, 
                        arrayOf(Telephony.Sms.THREAD_ID), 
                        null, 
                        null, 
                        null
                    )
                    
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val threadId = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                            addressToThreadId[address] = threadId
                            Timber.d("[Mobile] DEBUG [Restore] 为地址 $address 获取到threadId: $threadId")
                        }
                    }
                }
                
                // 报告进度
                if (processedCount % progressStep == 0) {
                    val currentProgress = (processedCount * 100) / totalCount
                    val progressMessage = "恢复短信进度: $processedCount/$totalCount"
                    progressCallback?.onProgressUpdate("短信", currentProgress, progressMessage)
                    Timber.d("[Mobile] DEBUG [Restore] $progressMessage ($currentProgress%)")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Restore] 恢复第一条短信失败: 地址=${address}, ${e.message}")
            }
        }
        
        // 然后处理每个联系人的剩余消息，使用相同的threadId
        for ((address, addressMessages) in messagesByAddress) {
            if (address.isNullOrBlank() || addressMessages.size <= 1) continue
            
            val threadId = addressToThreadId[address]
            if (threadId == null) {
                Timber.w("[Mobile] WARN [Restore] 未找到地址 $address 的threadId，跳过剩余消息")
                continue
            }
            
            // 从第二条消息开始处理
            for (i in 1 until addressMessages.size) {
                try {
                    val message = addressMessages[i]
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, message.body ?: "")
                        put(Telephony.Sms.DATE, message.date)
                        put(Telephony.Sms.DATE_SENT, message.date)
                        put(Telephony.Sms.READ, message.readState ?: 0)
                        put(Telephony.Sms.TYPE, message.type)
                        put(Telephony.Sms.STATUS, message.messageStatus ?: 0)
                        put(Telephony.Sms.THREAD_ID, threadId) // 使用相同的threadId
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
                    }
                    
                    processedCount++
                    // 报告进度
                    if (processedCount % progressStep == 0 || processedCount == totalCount) {
                        val currentProgress = (processedCount * 100) / totalCount
                        val progressMessage = "恢复短信进度: $processedCount/$totalCount"
                        progressCallback?.onProgressUpdate("短信", currentProgress, progressMessage)
                        Timber.d("[Mobile] DEBUG [Restore] $progressMessage ($currentProgress%)")
                    }
                    
                    // 每处理10条消息暂停一下，避免系统过载
                    if (i % 10 == 0) {
                        delay(50)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 恢复短信失败: 地址=${address}, ${e.message}")
                }
            }
            
            // 每处理完一个联系人的所有消息后暂停一下
            delay(100)
        }
        
        Timber.i("[Mobile] INFO [Restore] 短信恢复完成: 成功=$restoredCount, 总数=$totalCount")
        return@withContext restoredCount
    }
    
    /**
     * 验证并修复短信地址列表，避免出现"unknown"联系人问题
     * 
     * 此方法检查并修复备份数据中可能存在的问题：
     * 1. 空地址会导致短信显示为"未知"联系人
     * 2. 重复的ThreadID问题导致短信被错误地归类
     * 
     * @param messages 原始短信列表
     * @return 修复后的短信列表
     */
    private fun validateAndFixMessageAddresses(messages: List<Message>): List<Message> {
        Timber.d("[Mobile] DEBUG [Restore] 开始验证和修复短信地址，数量: ${messages.size}")
        
        // 统计空地址数量
        val emptyAddressCount = messages.count { it.address.isNullOrBlank() }
        if (emptyAddressCount > 0) {
            Timber.w("[Mobile] WARN [Restore] 发现 $emptyAddressCount 条短信缺少有效地址，将进行修复")
        }
        
        // 修复短信地址
        return messages.map { originalMessage ->
            // 只有当地址真的为空或空白时才生成替代地址
            if (originalMessage.address.isNullOrBlank()) {
                // 创建新消息对象而不是修改原对象，避免引用问题
                Message(
                    id = originalMessage.id,
                    address = "unknown_${originalMessage.id}", // 使用ID作为唯一标识符
                    body = originalMessage.body,
                    date = originalMessage.date,
                    type = originalMessage.type,
                    readState = originalMessage.readState,
                    messageStatus = originalMessage.messageStatus,
                    threadId = originalMessage.threadId
                )
            } else {
                // 有效地址，直接使用原消息
                originalMessage
            }
        }
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
        
        // 检查权限
        if (!hasCallLogPermissions()) {
            Timber.e("[Mobile] ERROR [Restore] 没有通话记录权限，无法恢复通话记录")
            return@withContext 0
        }
        
        // 计算进度更新频率
        val progressStep = if (totalCount > 100) totalCount / 10 else 1
        
        // 修复通话记录中的空号码问题
        val fixedCallLogs = callLogs.map { callLog ->
            if (callLog.number.isNullOrBlank()) {
                // 创建新对象以避免修改原始数据
                ModelCallLog(
                    id = callLog.id,
                    number = "unknown_${callLog.id}",
                    type = callLog.type,
                    date = callLog.date,
                    duration = callLog.duration,
                    contact = callLog.contact
                )
            } else {
                callLog
            }
        }
        
        // 尝试从联系人数据库获取联系人名称
        val numberToContactName = mutableMapOf<String, String>()
        try {
            for (callLog in fixedCallLogs) {
                if (callLog.number.isNullOrBlank() || numberToContactName.containsKey(callLog.number)) continue
                
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(callLog.number))
                contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val contactName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                        if (!contactName.isNullOrBlank()) {
                            numberToContactName[callLog.number] = contactName
                            Timber.d("[Mobile] DEBUG [Restore] 为号码 ${callLog.number} 找到联系人: $contactName")
                        }
                    }
                }
            }
            Timber.d("[Mobile] DEBUG [Restore] 从联系人数据库中找到 ${numberToContactName.size} 个联系人")
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Restore] 查询联系人数据库失败: ${e.message}")
        }
        
        // 按批次处理大量通话记录
        val batchSize = 50
        val totalBatches = (fixedCallLogs.size + batchSize - 1) / batchSize
        
        for (batchIndex in 0 until totalBatches) {
            val startIndex = batchIndex * batchSize
            val endIndex = minOf(startIndex + batchSize, fixedCallLogs.size)
            val currentBatch = fixedCallLogs.subList(startIndex, endIndex)
            
            currentBatch.forEachIndexed { indexInBatch, callLog ->
                try {
                    val index = startIndex + indexInBatch
                    val values = ContentValues().apply {
                        put(CallLog.Calls.NUMBER, callLog.number)
                        put(CallLog.Calls.TYPE, callLog.type)
                        
                        // 确保日期值有效
                        val validDate = if (callLog.date > 0) callLog.date else System.currentTimeMillis()
                        put(CallLog.Calls.DATE, validDate)
                        
                        // 确保通话时长有效
                        val validDuration = if (callLog.duration >= 0) callLog.duration else 0
                        put(CallLog.Calls.DURATION, validDuration)
                        
                        // 首先使用备份中的联系人名称
                        var contactName = callLog.contact
                        
                        // 如果备份中没有联系人名称，尝试使用从联系人数据库中获取的名称
                        if (contactName.isNullOrBlank()) {
                            contactName = numberToContactName[callLog.number]
                        }
                        
                        // 设置联系人名称
                        if (!contactName.isNullOrBlank()) {
                            put(CallLog.Calls.CACHED_NAME, contactName)
                            put(CallLog.Calls.CACHED_NUMBER_TYPE, CallLog.Calls.PRESENTATION_ALLOWED)
                        }
                    }
                    
                    val uri = contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
                    if (uri != null) {
                        restoredCount++
                        Timber.d("[Mobile] DEBUG [Restore] 成功恢复通话记录: 号码=${callLog.number}, 类型=${callLog.type}, 日期=${callLog.date}")
                    } else {
                        Timber.w("[Mobile] WARN [Restore] 恢复通话记录失败: 插入返回null, 号码=${callLog.number}")
                    }
                    
                    // 报告进度
                    if (index % progressStep == 0 || index == totalCount - 1) {
                        val currentProgress = ((index + 1) * 100) / totalCount
                        val progressMessage = "恢复通话记录进度: ${index + 1}/$totalCount"
                        progressCallback?.onProgressUpdate("通话记录", currentProgress, progressMessage)
                        Timber.d("[Mobile] DEBUG [Restore] $progressMessage ($currentProgress%)")
                    }
                    
                    // 每处理10条记录暂停一下，避免系统过载
                    if (indexInBatch % 10 == 0) {
                        delay(50)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 恢复通话记录失败: ID=${callLog.id}, 号码=${callLog.number}, ${e.message}")
                }
            }
            
            // 批次处理完成后让系统有时间处理数据
            delay(200)
        }
        
        Timber.i("[Mobile] INFO [Restore] 通话记录恢复完成: 成功=$restoredCount, 总数=$totalCount")
        return@withContext restoredCount
    }
    
    /**
     * 检查是否有通话记录权限
     */
    private fun hasCallLogPermissions(): Boolean {
        val readPermission = android.Manifest.permission.READ_CALL_LOG
        val writePermission = android.Manifest.permission.WRITE_CALL_LOG
        
        val readGranted = context.checkSelfPermission(readPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val writeGranted = context.checkSelfPermission(writePermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!readGranted || !writeGranted) {
            Timber.e("[Mobile] ERROR [Restore] 缺少通话记录权限: READ_CALL_LOG=$readGranted, WRITE_CALL_LOG=$writeGranted")
            return false
        }
        
        return true
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
                    progressCallback?.onProgressUpdate("联系人", currentProgress, progressMessage)
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
            
            // 检查高版本Android上是否有特殊情况
            if (!isDefault && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 在Android 12+上可能有额外的默认应用检查逻辑
                try {
                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                    if (roleManager != null && roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)) {
                        Timber.i("[Mobile] INFO [Restore] RoleManager报告应用持有SMS角色，覆盖默认检查结果")
                        return true
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 检查RoleManager时出错")
                }
            }
            
            return isDefault
        }
        return true // 在较旧版本中不需要是默认短信应用
    }
    
    /**
     * 请求设置为默认短信应用
     * 
     * 此方法用于向系统请求将当前应用设置为默认短信应用。它会启动系统的"更改默认短信应用"对话框，
     * 用户在对话框中可以确认或取消设置。
     * 
     * 在Android 4.4 (KitKat)及以上版本中，只有被设置为默认短信应用的应用才能写入短信数据库。
     * 这是Android系统的安全限制，目的是防止恶意应用滥用短信功能。
     * 
     * 注意: 调用此方法会打开系统对话框，需要用户交互才能完成设置过程。
     * 设置结果将通过 onActivityResult 回调返回到 Activity。
     * 
     * 流程说明:
     * 1. 调用此方法打开系统对话框
     * 2. 用户选择"是"或"否"
     * 3. 系统返回结果到 onActivityResult
     * 4. 在 onActivityResult 中检查是否成功设置为默认短信应用
     * 5. 如果成功，继续恢复短信；如果失败，提示用户并终止恢复过程
     * 
     * @param activity 发起请求的Activity，用于接收结果回调
     */
    fun requestDefaultSmsApp(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName)
            activity.startActivityForResult(intent, DEFAULT_SMS_REQUEST_CODE)
        }
    }
}

