package imken.messagevault.mobile.data

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import imken.messagevault.mobile.BuildConfig
import imken.messagevault.mobile.api.ApiClient
import imken.messagevault.mobile.config.Config
import imken.messagevault.mobile.model.BackupData
import imken.messagevault.mobile.model.Contact
import imken.messagevault.mobile.model.Message
import imken.messagevault.mobile.model.CallLog as ModelCallLog
import imken.messagevault.mobile.models.BackupFile
import imken.messagevault.mobile.models.MessageData
import imken.messagevault.mobile.utils.Constants.SUPPORTED_VERSION
import imken.messagevault.mobile.utils.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers as KotlinDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileReader
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import android.app.Activity
import android.content.OperationApplicationException
import android.provider.Settings as AndroidSettings
import android.provider.CallLog as AndroidCallLog
import android.widget.Toast
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher

/**
 * 消息实体类，用于数据处理
 */
data class MessageEntity(
    val id: Long,
    val address: String?,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Int,
    val status: Int,
    val threadId: Long
)

/**
 * 通话记录实体类，用于数据处理
 */
data class CallLogsEntity(
    val id: Long,
    val number: String?,
    val name: String,
    val date: Long,
    val duration: Long,
    val type: Int
)

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
    suspend fun getAvailableBackups(): List<BackupFile> = withContext(KotlinDispatchers.IO) {
        val backupFiles = mutableListOf<BackupFile>()
        
        try {
            // 使用config获取备份目录名称而不是硬编码字符串
            val backupDir = File(context.getExternalFilesDir(null), config.getBackupDirectoryName())
            
            // 检查备份目录是否存在
            if (!backupDir.exists()) {
                Timber.w("[Mobile] WARN [Restore] 备份目录不存在: ${backupDir.absolutePath}")
                return@withContext emptyList<BackupFile>()
            }
            
            // 获取备份目录中的所有JSON文件
            val files = backupDir.listFiles { file ->
                file.isFile && file.name.endsWith(".json", ignoreCase = true)
            }
            
            if (files != null) {
                // 过滤无效的备份文件
                val validFiles = files.filter { validateBackupFile(it) }
                backupFiles.addAll(validFiles.map { file -> 
                    val deviceId = AndroidSettings.Secure.getString(
                        context.contentResolver, 
                        AndroidSettings.Secure.ANDROID_ID
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
                    
                    BackupFile(
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
    suspend fun parseBackupFile(backupFile: BackupFile): BackupData? = withContext(KotlinDispatchers.IO) {
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
    suspend fun restoreFromFile(backupFile: BackupFile): RestoreResult = withContext(KotlinDispatchers.IO) {
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
        backupFile: BackupFile, 
        progressCallback: ProgressCallback?
    ): RestoreResult = withContext(KotlinDispatchers.IO) {
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
    ): Int = withContext(KotlinDispatchers.IO) {
        var restoredCount = 0
        val totalCount = messages.size
        Timber.i("[Mobile] INFO [Restore] 开始恢复短信，总数: $totalCount")
        
        // 计算进度更新频率
        val progressStep = if (totalCount > 100) totalCount / 20 else 1
        
        // 验证并修复消息地址
        val fixedMessages = validateAndFixMessageAddresses(messages)
        
        // 按联系人分组短信，提高批量恢复效率
        val messagesByContact = fixedMessages.groupBy { it.address }
        
        var processedCount = 0
        
        // 恢复所有短信
        for ((address, contactMessages) in messagesByContact) {
            Timber.d("[Mobile] DEBUG [Restore] 处理联系人消息: 联系人=$address, 消息数量=${contactMessages.size}")
            
            // 排序消息按日期从旧到新
            val sortedMessages = contactMessages.sortedBy { it.date }
            
            for (smsMessage in sortedMessages) {
                try {
                    // 创建短信内容值
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, smsMessage.address)
                        put(Telephony.Sms.BODY, smsMessage.body)
                        put(Telephony.Sms.DATE, smsMessage.date)
                        put(Telephony.Sms.TYPE, smsMessage.type)
                        put(Telephony.Sms.READ, smsMessage.readState ?: 0)
                        put(Telephony.Sms.STATUS, smsMessage.messageStatus ?: 0)
                        
                        // 如果有线程ID
                        if (smsMessage.threadId != null && smsMessage.threadId > 0) {
                            put(Telephony.Sms.THREAD_ID, smsMessage.threadId)
                        }
                    }
                    
                    // 插入短信
                    val uri = contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
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
                    if (processedCount % 10 == 0) {
                        delay(50)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 恢复短信失败: 地址=${smsMessage.address}, ${e.message}")
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
        
        // 导入电话号码工具类
        val phoneNumberUtils = PhoneNumberUtils
        
        // 统计空地址数量
        val emptyAddressCount = messages.count { it.address.isNullOrBlank() }
        if (emptyAddressCount > 0) {
            Timber.w("[Mobile] WARN [Restore] 发现 $emptyAddressCount 条短信缺少有效地址，将进行修复")
        }
        
        // 修复并标准化短信地址
        return messages.map { originalMessage ->
            // 处理空地址情况
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
                // 标准化电话号码
                val normalizedAddress = phoneNumberUtils.normalizePhoneNumber(originalMessage.address)
                
                // 如果地址标准化后不同，创建新的消息对象
                if (normalizedAddress != originalMessage.address) {
                    Timber.d("[Mobile] DEBUG [Restore] 标准化地址: ${originalMessage.address} -> $normalizedAddress")
                    
                    Message(
                        id = originalMessage.id,
                        address = normalizedAddress, // 使用标准化后的地址
                        body = originalMessage.body,
                        date = originalMessage.date,
                        type = originalMessage.type,
                        readState = originalMessage.readState,
                        messageStatus = originalMessage.messageStatus,
                        threadId = originalMessage.threadId
                    )
                } else {
                    // 有效地址且无需标准化，直接使用原消息
                    originalMessage
                }
            }
        }
    }
    
    /**
     * 分批恢复通话记录并报告进度
     */
    private suspend fun restoreCallLogsWithProgress(
        callLogs: List<ModelCallLog>,
        progressCallback: ProgressCallback?
    ): Int = withContext(KotlinDispatchers.IO) {
        var restoredCount = 0
        val totalCount = callLogs.size
        Timber.i("[Mobile] INFO [Restore] 开始恢复通话记录，总数: $totalCount")
        
        // 检查权限
        if (!hasCallLogPermissions()) {
            Timber.e("[Mobile] ERROR [Restore] 没有通话记录权限，无法恢复")
            return@withContext 0
        }
        
        // 计算进度更新频率
        val progressStep = if (totalCount > 100) totalCount / 20 else 1
        
        // 预处理通话记录，修复电话号码格式
        val fixedCallLogs = callLogs.map { callLog ->
            // 如果号码为空，使用原始记录
            if (callLog.number.isNullOrBlank()) {
                callLog
            } else {
                // 规范化电话号码
                val normalizedNumber = PhoneNumberUtils.normalizePhoneNumber(callLog.number)
                if (normalizedNumber != callLog.number) {
                    Timber.d("[Mobile] DEBUG [Restore] 通话记录号码已规范化: ${callLog.number} -> $normalizedNumber")
                    // 创建新对象并保留其他属性
                    callLog.copy(number = normalizedNumber)
                } else {
                    callLog
                }
            }
        }
        
        // 按联系人分组通话记录，提高批量恢复效率
        val callLogsByNumber = mutableMapOf<String, MutableList<ModelCallLog>>()
        
        // 为每个通话记录找到正确的分组键
        fixedCallLogs.forEach { callLog ->
            if (!callLog.number.isNullOrBlank()) {
                // 获取此号码的所有可能变体
                val normalized = PhoneNumberUtils.normalizePhoneNumber(callLog.number)
                
                // 将通话记录添加到对应的分组
                if (!callLogsByNumber.containsKey(normalized)) {
                    callLogsByNumber[normalized] = mutableListOf()
                }
                callLogsByNumber[normalized]!!.add(callLog)
            } else {
                // 处理号码为空的情况
                val unknownKey = "unknown_${callLog.id}"
                if (!callLogsByNumber.containsKey(unknownKey)) {
                    callLogsByNumber[unknownKey] = mutableListOf()
                }
                callLogsByNumber[unknownKey]!!.add(callLog)
            }
        }
        
        Timber.d("[Mobile] DEBUG [Restore] 按规范化号码分组后，共有 ${callLogsByNumber.size} 个不同联系人")
        
        var processedCount = 0
        
        // 恢复所有通话记录
        for ((_, numberCallLogs) in callLogsByNumber) {
            for (callLog in numberCallLogs) {
                try {
                    val values = ContentValues().apply {
                        put(AndroidCallLog.Calls.NUMBER, callLog.number ?: "")
                        put(AndroidCallLog.Calls.TYPE, callLog.type)
                        put(AndroidCallLog.Calls.DATE, callLog.date.toLong())
                        put(AndroidCallLog.Calls.DURATION, callLog.duration.toLong())
                        put(AndroidCallLog.Calls.NEW, 0) // 标记为已读
                        
                        // 尝试匹配联系人
                        if (!callLog.number.isNullOrBlank()) {
                            val contactName = getContactNameFromNumber(callLog.number)
                            if (contactName != null) {
                                put(AndroidCallLog.Calls.CACHED_NAME, contactName)
                                Timber.d("[Mobile] DEBUG [Restore] 通话记录匹配到联系人: ${callLog.number} -> $contactName")
                            }
                        }
                    }
                    
                    val uri = contentResolver.insert(AndroidCallLog.Calls.CONTENT_URI, values)
                    if (uri != null) {
                        restoredCount++
                    }
                    
                    processedCount++
                    // 报告进度
                    if (processedCount % progressStep == 0 || processedCount == totalCount) {
                        val currentProgress = (processedCount * 100) / totalCount
                        val progressMessage = "恢复通话记录进度: $processedCount/$totalCount"
                        progressCallback?.onProgressUpdate("通话记录", currentProgress, progressMessage)
                        Timber.d("[Mobile] DEBUG [Restore] $progressMessage ($currentProgress%)")
                    }
                    
                    // 每处理10条记录暂停一下，避免系统过载
                    if (processedCount % 10 == 0) {
                        delay(50)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Restore] 恢复通话记录失败: 号码=${callLog.number}, ${e.message}")
                }
            }
            
            // 每处理完一个联系人的所有记录后暂停一下
            delay(100)
        }
        
        Timber.i("[Mobile] INFO [Restore] 通话记录恢复完成: 成功=$restoredCount, 总数=$totalCount")
        return@withContext restoredCount
    }
    
    /**
     * 从电话号码获取联系人姓名，使用电话号码变体增强匹配率
     */
    private fun getContactNameFromNumber(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        
        // 首先尝试直接匹配原始号码
        val name = getContactNameByExactNumber(phoneNumber)
        if (name != null) return name
        
        // 若直接匹配失败，尝试匹配所有可能的变体
        val variants = PhoneNumberUtils.getPossibleNumberVariants(phoneNumber)
        for (variant in variants) {
            val variantName = getContactNameByExactNumber(variant)
            if (variantName != null) {
                Timber.d("[Mobile] DEBUG [Restore] 通过号码变体匹配到联系人: $phoneNumber -> $variant -> $variantName")
                return variantName
            }
        }
        
        return null
    }
    
    /**
     * 通过精确号码查询联系人姓名
     */
    private fun getContactNameByExactNumber(phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        
        try {
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Restore] 查询联系人姓名失败: 号码=$phoneNumber, ${e.message}")
        }
        
        return null
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
    ): Int = withContext(KotlinDispatchers.IO) {
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
    private fun someRestoreMethod(backupFile: BackupFile) {
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
            read = this.readState ?: 0,
            status = this.messageStatus ?: 0,
            threadId = this.threadId ?: 0
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
            // 使用固定值0代替id，让Room自动生成主键
            id = 0L,
            number = this.number,
            name = this.contact ?: "",
            date = this.date.toLong(),
            duration = this.duration.toLong(),
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
            threadId = this.threadId?.toLong() ?: 0L
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
        const val PERMISSIONS_REQUEST_CODE = 1002
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
            try {
                // 优先使用RoleManager（Android 10+）
                var roleManagerResult = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                        if (roleManager != null) {
                            roleManagerResult = roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
                            Timber.d("[Mobile] DEBUG [Restore] RoleManager检查结果: $roleManagerResult")
                            if (roleManagerResult) {
                                return true
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "[Mobile] ERROR [Restore] 检查RoleManager时出错")
                    }
                }
                
                // 如果RoleManager检查未成功或不可用，使用传统方法
                val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
                val isTelephonyDefault = defaultSmsPackage == context.packageName
                
                // 添加详细日志
                Timber.d("[Mobile] DEBUG [Restore] 默认短信应用检查 - 当前应用: ${context.packageName}, 系统默认: $defaultSmsPackage, 是默认: $isTelephonyDefault")
                
                return isTelephonyDefault
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Restore] 检查默认短信应用状态时发生错误")
                return false
            }
        }
        return true // 在较旧版本中不需要是默认短信应用
    }
    
    /**
     * 请求设置为默认短信应用
     * 
     * 此方法用于向系统请求将当前应用设置为默认短信应用。它应该启动来自调用Activity的
     * ActivityResultLauncher而不是直接调用startActivityForResult。
     * 
     * 在Android 4.4 (KitKat)及以上版本中，只有被设置为默认短信应用的应用才能写入短信数据库。
     * 这是Android系统的安全限制，目的是防止恶意应用滥用短信功能。
     * 
     * @param activity 发起请求的Activity，用于获取RoleManager和创建Intent
     * @param launcher ActivityResultLauncher<Intent>用于处理结果回调
     */
    fun requestDefaultSmsApp(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                Timber.d("[Mobile] DEBUG [Restore] 开始请求默认短信应用权限，Android版本: ${Build.VERSION.SDK_INT}")
                var requestSent = false
                
                // 尝试使用RoleManager API (Android 10+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
                        if (roleManager != null) {
                            // 检查角色是否可用
                            if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                                // 检查应用是否已持有角色
                                val hasRole = roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_SMS)
                                Timber.d("[Mobile] DEBUG [Restore] RoleManager检查结果: $hasRole")
                                
                                if (!hasRole) {
                                    // 使用ActivityResultLauncher请求SMS角色
                                    val roleRequestIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                                    launcher.launch(roleRequestIntent)
                                    Timber.d("[Mobile] DEBUG [Restore] 已使用角色管理器发送SMS角色请求")
                                    requestSent = true
                                } else {
                                    Timber.i("[Mobile] INFO [Restore] 应用已持有SMS角色，无需再次请求")
                                    return
                                }
                            } else {
                                Timber.w("[Mobile] WARN [Restore] 此设备的RoleManager不支持SMS角色")
                            }
                        } else {
                            Timber.w("[Mobile] WARN [Restore] 无法获取RoleManager服务")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "[Mobile] ERROR [Restore] 使用RoleManager请求SMS角色失败: ${e.message}")
                    }
                }
                
                // 如果RoleManager方法未成功，使用传统方法
                if (!requestSent) {
                    // 传统方法（适用于Android 4.4+）
                    val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.packageName)
                    
                    if (intent.resolveActivity(activity.packageManager) != null) {
                        launcher.launch(intent)
                        Timber.d("[Mobile] DEBUG [Restore] 已发送默认短信应用请求（传统方法）")
                        requestSent = true
                    } else {
                        Timber.w("[Mobile] WARN [Restore] 无法找到处理默认短信应用请求的系统组件")
                    }
                }
                
                // 如果上面的方法都失败，尝试使用备选方法
                if (!requestSent) {
                    // 尝试打开系统默认应用设置页面
                    try {
                        // 先尝试使用默认应用设置页面
                        val defaultAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                        if (defaultAppsIntent.resolveActivity(activity.packageManager) != null) {
                            activity.startActivity(defaultAppsIntent)
                            Timber.d("[Mobile] DEBUG [Restore] 已打开默认应用设置页面")
                            
                            // 提示用户手动设置
                            Toast.makeText(
                                activity, 
                                "请在默认应用设置中将本应用设为默认短信应用", 
                                Toast.LENGTH_LONG
                            ).show()
                            requestSent = true
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "[Mobile] ERROR [Restore] 无法打开默认应用设置: ${e.message}")
                    }
                    
                    // 如果默认应用设置页面也失败，尝试应用详情页
                    if (!requestSent) {
                        try {
                            val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            settingsIntent.data = Uri.parse("package:" + activity.packageName)
                            activity.startActivity(settingsIntent)
                            Timber.w("[Mobile] WARN [Restore] 无法直接请求默认短信权限，已打开应用设置页面")
                            
                            // 提示用户在设置中手动授予权限
                            Toast.makeText(
                                activity, 
                                "请在应用设置中授予短信相关权限，然后设置为默认短信应用", 
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Timber.e(e, "[Mobile] ERROR [Restore] 无法打开应用设置: ${e.message}")
                            
                            // 最后的尝试：打开系统设置主页
                            try {
                                val mainSettingsIntent = Intent(Settings.ACTION_SETTINGS)
                                activity.startActivity(mainSettingsIntent)
                                Toast.makeText(
                                    activity,
                                    "请在系统设置中找到应用管理，将本应用设为默认短信应用",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (finalException: Exception) {
                                Timber.e(finalException, "[Mobile] ERROR [Restore] 无法打开系统设置: ${finalException.message}")
                                Toast.makeText(
                                    activity,
                                    "无法自动打开设置，请手动将本应用设为默认短信应用",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Restore] 请求默认短信应用权限失败: ${e.message}")
                Toast.makeText(
                    activity,
                    "无法请求默认短信应用权限: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * 检测是否在模拟器环境中运行
     */
    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }
    
    /**
     * 检查并请求所需权限
     * 
     * 此方法检查恢复所需的权限，并根据需要请求缺失的权限。
     * 适用于Android 6.0+的动态权限请求。
     * 
     * @param activity 发起请求的Activity
     * @param requestCode 权限请求代码
     * @return 如果所有权限都已授予则返回true，否则返回false
     */
    fun checkAndRequestPermissions(activity: Activity, requestCode: Int): Boolean {
        // 所需权限列表
        val requiredPermissions = mutableListOf<String>()
        
        // 检查SMS权限
        if (!hasSmsPermissions()) {
            requiredPermissions.add(android.Manifest.permission.READ_SMS)
            requiredPermissions.add(android.Manifest.permission.SEND_SMS)
            requiredPermissions.add(android.Manifest.permission.RECEIVE_SMS)
        }
        
        // 检查通话记录权限
        if (!hasCallLogPermissions()) {
            requiredPermissions.add(android.Manifest.permission.READ_CALL_LOG)
            requiredPermissions.add(android.Manifest.permission.WRITE_CALL_LOG)
        }
        
        // 检查联系人权限
        if (!hasContactsPermissions()) {
            requiredPermissions.add(android.Manifest.permission.READ_CONTACTS)
            requiredPermissions.add(android.Manifest.permission.WRITE_CONTACTS)
        }
        
        // 如果有缺失权限，请求它们
        if (requiredPermissions.isNotEmpty()) {
            // 在Android 11+，媒体权限可能需要特殊处理
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // 在Android 11+，某些权限可能需要通过Intent请求
                val specialPermissions = mutableListOf<String>()
                val normalPermissions = mutableListOf<String>()
                
                for (permission in requiredPermissions) {
                    if (permission == android.Manifest.permission.READ_SMS ||
                        permission == android.Manifest.permission.READ_CALL_LOG ||
                        permission == android.Manifest.permission.WRITE_CALL_LOG) {
                        specialPermissions.add(permission)
                    } else {
                        normalPermissions.add(permission)
                    }
                }
                
                // 先请求普通权限
                if (normalPermissions.isNotEmpty()) {
                    activity.requestPermissions(normalPermissions.toTypedArray(), requestCode)
                }
                
                // 对于特殊权限，可能需要引导用户到设置页面
                if (specialPermissions.isNotEmpty()) {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:" + activity.packageName)
                        activity.startActivity(intent)
                        Timber.i("[Mobile] INFO [Restore] 在Android 11+上打开应用设置页面请求特殊权限")
                    } catch (e: Exception) {
                        Timber.e(e, "[Mobile] ERROR [Restore] 无法打开应用设置: ${e.message}")
                    }
                }
            } else {
                // Android 10及以下的正常权限请求
                activity.requestPermissions(requiredPermissions.toTypedArray(), requestCode)
                Timber.d("[Mobile] DEBUG [Restore] 请求权限: ${requiredPermissions.joinToString()}")
            }
            return false
        }
        
        return true // 所有权限都已授予
    }
}

