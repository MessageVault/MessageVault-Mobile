package imken.messagevault.mobile.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import android.provider.Telephony
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import imken.messagevault.mobile.model.BackupData
import imken.messagevault.mobile.model.Contact
import imken.messagevault.mobile.model.CallLog
import imken.messagevault.mobile.model.Message
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 备份管理器
 * 
 * 负责执行备份操作，包括短信、通话记录和联系人的备份
 */
class BackupManager(private val context: Context) {
    
    // 使用优化的Gson配置，减小JSON文件大小
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        // .excludeFieldsWithoutExposeAnnotation() // 移除此配置，因为可能导致序列化问题
        .serializeNulls() // 允许序列化null值
        .disableHtmlEscaping() // 不转义HTML字符
        .setLenient() // 使用宽松模式解析
        .setPrettyPrinting() // 启用美化打印，使JSON输出格式化
        .create()
    
    /**
     * 执行备份操作
     * 
     * @return 备份结果，包含备份的短信、通话记录和联系人数量
     */
    suspend fun performBackup(): BackupResult {
        try {
            Timber.i("[Mobile] INFO [Backup] 开始备份; Context: 用户请求")
            
            // 检查是否有必要的权限
            val hasSmsPermission = context.checkSelfPermission(android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCallLogPermission = context.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasContactsPermission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasSmsPermission && !hasCallLogPermission && !hasContactsPermission) {
                Timber.e("[Mobile] ERROR [Backup] 备份失败: 没有任何所需权限")
                return BackupResult(
                    timestamp = Date(),
                    messagesCount = 0,
                    callLogsCount = 0,
                    contactsCount = 0,
                    backupFilePath = null,
                    errorMessage = "备份失败: 没有任何所需权限"
                )
            }
            
            // 读取短信
            val messages = if (hasSmsPermission) readMessages() else null
            val messagesCount = messages?.size ?: 0
            Timber.i("[Mobile] INFO [Backup] 读取到 $messagesCount 条短信")
            
            // 读取通话记录
            val callLogs = if (hasCallLogPermission) readCallLogs() else null
            val callLogsCount = callLogs?.size ?: 0
            Timber.i("[Mobile] INFO [Backup] 读取到 $callLogsCount 条通话记录")
            
            // 读取联系人
            val contacts = if (hasContactsPermission) readContacts() else null
            val contactsCount = contacts?.size ?: 0
            Timber.i("[Mobile] INFO [Backup] 读取到 $contactsCount 个联系人")
            
            // 检查是否有任何数据可以备份
            if (messagesCount == 0 && callLogsCount == 0 && contactsCount == 0) {
                Timber.w("[Mobile] WARN [Backup] 没有找到任何数据可以备份")
                return BackupResult(
                    timestamp = Date(),
                    messagesCount = 0,
                    callLogsCount = 0,
                    contactsCount = 0,
                    backupFilePath = null,
                    errorMessage = "没有找到任何数据可以备份"
                )
            }
            
            // 创建备份数据对象
            val backupData = BackupData(
                messages = messages,
                callLogs = callLogs,
                contacts = contacts,
                timestamp = System.currentTimeMillis(),
                deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"
            )
            
            // 保存到本地文件
            val backupFile = createLocalBackup(backupData)
            
            if (backupFile == null) {
                Timber.e("[Mobile] ERROR [Backup] 创建备份文件失败")
                return BackupResult(
                    timestamp = Date(),
                    messagesCount = messagesCount,
                    callLogsCount = callLogsCount,
                    contactsCount = contactsCount,
                    backupFilePath = null,
                    errorMessage = "创建备份文件失败"
                )
            }
            
            Timber.i("[Mobile] INFO [Backup] 备份完成; Context: 保存至 ${backupFile.absolutePath}")
            
            // 返回备份结果
            return BackupResult(
                timestamp = Date(),
                messagesCount = messagesCount,
                callLogsCount = callLogsCount,
                contactsCount = contactsCount,
                backupFilePath = backupFile.absolutePath
            )
            
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Backup] 备份过程出现异常; Context: ${e.message}")
            return BackupResult(
                timestamp = Date(),
                messagesCount = 0,
                callLogsCount = 0,
                contactsCount = 0,
                backupFilePath = null,
                errorMessage = "备份过程出现异常: ${e.message}"
            )
        }
    }
    
    /**
     * 创建本地备份文件
     */
    private fun createLocalBackup(backupData: BackupData): File? {
        try {
            // 创建备份目录
            val backupDir = File(context.getExternalFilesDir(null), "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            
            // 生成友好的文件名而不是使用base64编码
            val fileName = generateUserFriendlyFileName()
            
            // 创建备份文件
            val backupFile = File(backupDir, fileName)
            
            // 将备份数据转换为JSON前单独记录各部分数据的状态
            Timber.d("[Mobile] DEBUG [Backup] 数据准备情况: 短信=${backupData.messages?.size ?: 0}, 通话记录=${backupData.callLogs?.size ?: 0}, 联系人=${backupData.contacts?.size ?: 0}, 设备信息=${backupData.deviceInfo}")
            
            try {
                // 尝试分段序列化，先只序列化基本字段
                val testData = BackupData(
                    messages = null,
                    callLogs = null,
                    contacts = null,
                    timestamp = backupData.timestamp,
                    deviceInfo = backupData.deviceInfo
                )
                
                // 测试序列化基本结构
                val testJson = gson.toJson(testData)
                if (testJson.isNullOrBlank() || testJson == "{}" || testJson == "null") {
                    Timber.e("[Mobile] ERROR [Backup] 基本数据结构序列化失败")
                } else {
                    Timber.d("[Mobile] DEBUG [Backup] 基本数据结构序列化成功: $testJson")
                }
                
                // 逐步添加数据进行序列化测试
                try {
                    val messagesJson = gson.toJson(backupData.messages)
                    Timber.d("[Mobile] DEBUG [Backup] 短信JSON长度: ${messagesJson.length}, 前100个字符: ${messagesJson.take(100)}")
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Backup] 短信数据序列化失败: ${e.message}")
                }
                
                try {
                    val callLogsJson = gson.toJson(backupData.callLogs)
                    Timber.d("[Mobile] DEBUG [Backup] 通话记录JSON长度: ${callLogsJson.length}, 前100个字符: ${callLogsJson.take(100)}")
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Backup] 通话记录数据序列化失败: ${e.message}")
                }
                
                try {
                    val contactsJson = gson.toJson(backupData.contacts)
                    Timber.d("[Mobile] DEBUG [Backup] 联系人JSON长度: ${contactsJson.length}, 前100个字符: ${contactsJson.take(100)}")
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Backup] 联系人数据序列化失败: ${e.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Backup] 分段序列化测试过程中出现异常: ${e.message}")
            }
            
            // 尝试创建一个清理过的备份数据对象
            // 检查联系人数据是否有问题
            var cleanedContacts = backupData.contacts?.map { contact ->
                // 创建一个具有基本字段的新联系人对象
                Contact(
                    id = contact.id,
                    name = contact.name.replace(Regex("[^\\p{Print}]"), ""), // 移除不可打印字符
                    phoneNumbers = contact.phoneNumbers.map { it.replace(Regex("[^\\p{Print}]"), "") }.toMutableList(),
                    emails = contact.emails?.map { it.replace(Regex("[^\\p{Print}]"), "") },
                    note = contact.note?.replace(Regex("[^\\p{Print}]"), ""),
                    addresses = contact.addresses?.map { 
                        Contact.Address(
                            type = it.type.replace(Regex("[^\\p{Print}]"), ""),
                            value = it.value.replace(Regex("[^\\p{Print}]"), "")
                        ) 
                    },
                    groups = contact.groups?.map { it.replace(Regex("[^\\p{Print}]"), "") }
                )
            }
            
            // 清理消息数据
            var cleanedMessages = backupData.messages?.map { message ->
                try {
                    Message(
                        id = message.id,
                        address = message.address.replace(Regex("[^\\p{Print}]"), ""),
                        body = message.body?.replace(Regex("[^\\p{Print}]"), ""),
                        date = message.date,
                        type = message.type,
                        readState = message.readState,
                        messageStatus = message.messageStatus,
                        threadId = message.threadId
                    )
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Backup] 清理短信数据时出错: ${e.message}")
                    null
                }
            }?.filterNotNull()
            
            // 清理通话记录数据
            var cleanedCallLogs = backupData.callLogs?.map { callLog ->
                try {
                    CallLog(
                        id = callLog.id,
                        number = callLog.number.replace(Regex("[^\\p{Print}]"), ""),
                        type = callLog.type,
                        date = callLog.date,
                        duration = callLog.duration,
                        contact = callLog.contact?.replace(Regex("[^\\p{Print}]"), "")
                    )
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Backup] 清理通话记录数据时出错: ${e.message}")
                    null
                }
            }?.filterNotNull()
            
            // 创建清理后的备份数据
            val cleanedBackupData = BackupData(
                messages = cleanedMessages,
                callLogs = cleanedCallLogs,
                contacts = cleanedContacts,
                timestamp = backupData.timestamp,
                deviceInfo = backupData.deviceInfo
            )
            
            // 完整序列化
            try {
                Timber.d("[Mobile] DEBUG [Backup] 尝试使用清理后的数据进行序列化")
                
                // 先创建一个超简化的备份数据对象，仅包含基本信息
                val minimalBackupData = BackupData(
                    messages = emptyList(),
                    callLogs = emptyList(),
                    contacts = emptyList(),
                    timestamp = cleanedBackupData.timestamp,
                    deviceInfo = cleanedBackupData.deviceInfo
                )
                
                // 测试序列化最小数据
                val minimalJson = gson.toJson(minimalBackupData)
                if (minimalJson.isNullOrBlank() || minimalJson == "{}" || minimalJson == "null") {
                    Timber.e("[Mobile] ERROR [Backup] 最小数据结构序列化失败，这表明Gson配置有问题")
                    return null
                }
                
                Timber.d("[Mobile] DEBUG [Backup] 最小数据序列化成功: $minimalJson")
                
                // 创建一个可能最终使用的数据结构
                var finalMessages = cleanedMessages
                var finalCallLogs = cleanedCallLogs
                var finalContacts = cleanedContacts
                
                // 尝试序列化短信数据
                if (!cleanedMessages.isNullOrEmpty()) {
                    try {
                        val testMessagesJson = gson.toJson(cleanedMessages)
                        Timber.d("[Mobile] DEBUG [Backup] 短信序列化测试成功，大小: ${testMessagesJson.length} 字节")
                    } catch (e: Exception) {
                        Timber.e(e, "[Mobile] ERROR [Backup] 短信数据序列化失败，使用空列表: ${e.message}")
                        finalMessages = emptyList()
                    }
                }
                
                // 尝试序列化通话记录数据
                if (!cleanedCallLogs.isNullOrEmpty()) {
                    try {
                        val testCallLogsJson = gson.toJson(cleanedCallLogs)
                        Timber.d("[Mobile] DEBUG [Backup] 通话记录序列化测试成功，大小: ${testCallLogsJson.length} 字节")
                    } catch (e: Exception) {
                        Timber.e(e, "[Mobile] ERROR [Backup] 通话记录数据序列化失败，使用空列表: ${e.message}")
                        finalCallLogs = emptyList()
                    }
                }
                
                // 尝试序列化联系人数据
                if (!cleanedContacts.isNullOrEmpty()) {
                    try {
                        val testContactsJson = gson.toJson(cleanedContacts)
                        Timber.d("[Mobile] DEBUG [Backup] 联系人序列化测试成功，大小: ${testContactsJson.length} 字节")
                    } catch (e: Exception) {
                        Timber.e(e, "[Mobile] ERROR [Backup] 联系人数据序列化失败，使用空列表: ${e.message}")
                        finalContacts = emptyList()
                    }
                }
                
                // 创建最终备份数据
                val finalBackupData = BackupData(
                    messages = finalMessages,
                    callLogs = finalCallLogs,
                    contacts = finalContacts,
                    timestamp = cleanedBackupData.timestamp,
                    deviceInfo = cleanedBackupData.deviceInfo
                )
                
                // 创建完整的JSON数据
                val jsonString: String
                try {
                    jsonString = gson.toJson(finalBackupData)
                    
                    // 验证JSON数据不为空且格式正确
                    if (jsonString.isBlank() || jsonString == "{}" || jsonString == "null") {
                        Timber.e("[Mobile] ERROR [Backup] 创建的备份数据为空或格式无效")
                        return null
                    }
                    
                    // 尝试解析JSON以验证格式有效
                    gson.fromJson(jsonString, BackupData::class.java)
                    Timber.d("[Mobile] DEBUG [Backup] JSON格式验证成功")
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Backup] JSON序列化或格式验证失败: ${e.message}")
                    return null
                }
                
                // 记录数据大小信息
                val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
                Timber.d("[Mobile] DEBUG [Backup] JSON数据大小: ${jsonBytes.size} 字节")
                
                // 如果文件太大（超过10MB），尝试仅保存部分数据
                var finalJsonString = jsonString
                if (jsonBytes.size > 10 * 1024 * 1024) {
                    Timber.w("[Mobile] WARN [Backup] JSON数据超过10MB，尝试减少数据量")
                    
                    // 创建一个精简版的备份数据（限制每种类型最多500条记录）
                    val limitedBackupData = BackupData(
                        messages = finalMessages?.take(500),
                        callLogs = finalCallLogs?.take(500),
                        contacts = finalContacts?.take(500),
                        timestamp = cleanedBackupData.timestamp,
                        deviceInfo = "${cleanedBackupData.deviceInfo} (数据已精简，原始数据: ${finalMessages?.size ?: 0}条短信, ${finalCallLogs?.size ?: 0}条通话记录, ${finalContacts?.size ?: 0}个联系人)"
                    )
                    
                    finalJsonString = gson.toJson(limitedBackupData)
                    Timber.d("[Mobile] DEBUG [Backup] 精简后的JSON数据大小: ${finalJsonString.toByteArray(Charsets.UTF_8).size} 字节")
                }
                
                // 将备份数据写入文件
                try {
                    backupFile.writeText(finalJsonString)
                    Timber.d("[Mobile] DEBUG [Backup] 成功将数据写入文件")
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Backup] 写入文件失败: ${e.message}")
                    return null
                }
                
                // 验证写入的文件大小
                if (!backupFile.exists() || backupFile.length() <= 10) { // 空的JSON至少有"{}"两个字符
                    Timber.e("[Mobile] ERROR [Backup] 创建的备份文件为空或过小: ${backupFile.length()} 字节")
                    backupFile.delete() // 删除无效文件
                    return null
                }
                
                Timber.i("[Mobile] INFO [Backup] 成功创建备份文件: ${backupFile.absolutePath}, 大小: ${backupFile.length()} 字节")
                return backupFile
            } catch (e: Exception) {
                Timber.e(e, "[Mobile] ERROR [Backup] JSON序列化过程中出现异常: ${e.message}")
                return null
            }
            
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Backup] 创建本地备份文件失败; Context: ${e.message}")
            return null
        }
    }
    
    /**
     * 生成用户友好的文件名
     * 格式: MessageVault_设备名_yyyy-MM-dd_HH-mm.json
     */
    private fun generateUserFriendlyFileName(deviceName: String? = null): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val device = deviceName ?: Build.MODEL.replace(" ", "_")
        
        return "MessageVault_${device}_${timestamp}.json"
    }
    
    private fun readMessages(): List<imken.messagevault.mobile.model.Message>? {
        val messages = mutableListOf<imken.messagevault.mobile.model.Message>()
        
        try {
            // 检查权限
            val permissionStatus = context.checkSelfPermission(android.Manifest.permission.READ_SMS)
            if (permissionStatus != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Timber.e("[Mobile] ERROR [Backup] 备份短信失败: 没有 READ_SMS 权限")
                return null
            }
            
            Timber.d("[Mobile] DEBUG [Backup] 开始读取短信...")
            
            // 查询短信内容提供者
            val uri = android.provider.Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                android.provider.Telephony.Sms._ID,
                android.provider.Telephony.Sms.ADDRESS,
                android.provider.Telephony.Sms.BODY,
                android.provider.Telephony.Sms.DATE,
                android.provider.Telephony.Sms.TYPE,
                android.provider.Telephony.Sms.READ,
                android.provider.Telephony.Sms.STATUS
            )
            val sortOrder = "${android.provider.Telephony.Sms.DATE} DESC"

            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->

                Timber.d("[Mobile] DEBUG [Backup] 找到 ${cursor.count} 条短信")

                val idColumn = cursor.getColumnIndex(android.provider.Telephony.Sms._ID)
                val addressColumn = cursor.getColumnIndex(android.provider.Telephony.Sms.ADDRESS)
                val bodyColumn = cursor.getColumnIndex(android.provider.Telephony.Sms.BODY)
                val dateColumn = cursor.getColumnIndex(android.provider.Telephony.Sms.DATE)
                val typeColumn = cursor.getColumnIndex(android.provider.Telephony.Sms.TYPE)
                val readColumn = cursor.getColumnIndex(android.provider.Telephony.Sms.READ)
                val statusColumn = cursor.getColumnIndex(android.provider.Telephony.Sms.STATUS)

                while (cursor.moveToNext()) {
                    val id = if (idColumn != -1) cursor.getLong(idColumn) else 0
                    val address = if (addressColumn != -1) cursor.getString(addressColumn) else ""
                    val body = if (bodyColumn != -1) cursor.getString(bodyColumn) else ""
                    val date = if (dateColumn != -1) cursor.getLong(dateColumn) else 0
                    val type = if (typeColumn != -1) cursor.getInt(typeColumn) else 0
                    val read = if (readColumn != -1) cursor.getInt(readColumn) else 0
                    val status = if (statusColumn != -1) cursor.getInt(statusColumn) else 0

                    val message = imken.messagevault.mobile.model.Message(
                        id = id,
                        address = address,
                        body = body,
                        date = date,
                        type = type,
                        readState = read,
                        messageStatus = status
                    )

                    messages.add(message)
                }
            } ?: run {
                Timber.e("[Mobile] ERROR [Backup] 备份短信失败: 无法查询短信内容提供者")
                return null
            }

            Timber.i("[Mobile] INFO [Backup] 成功读取 ${messages.size} 条短信")

        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Backup] 备份短信异常: ${e.message}")
            return null
        }

        return messages
    }
    
    private fun readCallLogs(): List<imken.messagevault.mobile.model.CallLog>? {
        val callLogs = mutableListOf<imken.messagevault.mobile.model.CallLog>()
        
        try {
            // 检查权限
            val permissionStatus = context.checkSelfPermission(android.Manifest.permission.READ_CALL_LOG)
            if (permissionStatus != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Timber.e("[Mobile] ERROR [Backup] 备份通话记录失败: 没有 READ_CALL_LOG 权限")
                return null
            }
            
            Timber.d("[Mobile] DEBUG [Backup] 开始读取通话记录...")
            
            // 查询通话记录
            val uri = android.provider.CallLog.Calls.CONTENT_URI
            val projection = arrayOf(
                android.provider.CallLog.Calls._ID,
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.CACHED_NAME,
                android.provider.CallLog.Calls.DATE,
                android.provider.CallLog.Calls.DURATION,
                android.provider.CallLog.Calls.TYPE
            )
            val sortOrder = "${android.provider.CallLog.Calls.DATE} DESC"
            
            // 使用分批查询减轻内存压力，每批100条记录
            val timeRanges = generateTimeRanges()
            var totalCallLogs = 0
            
            for (range in timeRanges) {
                val selection = "${android.provider.CallLog.Calls.DATE} >= ? AND ${android.provider.CallLog.Calls.DATE} <= ?"
                val selectionArgs = arrayOf(range.first.toString(), range.second.toString())
                
                Timber.d("[Mobile] DEBUG [Backup] 查询时间范围: ${range.first} 到 ${range.second}")
                
                try {
                    context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                        totalCallLogs += cursor.count
                        
                        val idColumn = cursor.getColumnIndex(android.provider.CallLog.Calls._ID)
                        val numberColumn = cursor.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                        val nameColumn = cursor.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
                        val dateColumn = cursor.getColumnIndex(android.provider.CallLog.Calls.DATE)
                        val durationColumn = cursor.getColumnIndex(android.provider.CallLog.Calls.DURATION)
                        val typeColumn = cursor.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                        
                        Timber.d("[Mobile] DEBUG [Backup] 批次查询到 ${cursor.count} 条通话记录")
                        
                        while (cursor.moveToNext()) {
                            try {
                                val id = if (idColumn != -1 && !cursor.isNull(idColumn)) cursor.getLong(idColumn) else 0L
                                
                                // 电话号码处理，确保不为null
                                val number = if (numberColumn != -1 && !cursor.isNull(numberColumn)) {
                                    cursor.getString(numberColumn)
                                } else {
                                    "unknown" // 使用默认值代替null
                                }
                                
                                // 联系人名称
                                val name = if (nameColumn != -1 && !cursor.isNull(nameColumn)) {
                                    cursor.getString(nameColumn)
                                } else {
                                    null // 联系人名称可以为null
                                }
                                
                                // 时间戳处理
                                val date = if (dateColumn != -1 && !cursor.isNull(dateColumn)) {
                                    cursor.getLong(dateColumn)
                                } else {
                                    System.currentTimeMillis() // 使用当前时间作为默认值
                                }
                                
                                // 通话时长
                                val duration = if (durationColumn != -1 && !cursor.isNull(durationColumn)) {
                                    cursor.getInt(durationColumn)
                                } else {
                                    0 // 默认为0秒
                                }
                                
                                // 通话类型
                                val type = if (typeColumn != -1 && !cursor.isNull(typeColumn)) {
                                    cursor.getInt(typeColumn)
                                } else {
                                    android.provider.CallLog.Calls.INCOMING_TYPE // 默认为来电类型
                                }
                                
                                // 构建通话记录对象并添加到列表
                                val callLog = imken.messagevault.mobile.model.CallLog(
                                    id = id,
                                    number = number,
                                    contact = name,
                                    date = date,
                                    duration = duration,
                                    type = type
                                )
                                
                                callLogs.add(callLog)
                            } catch (e: Exception) {
                                Timber.e(e, "[Mobile] ERROR [Backup] 处理单条通话记录失败: ${e.message}")
                                // 继续处理下一条记录
                            }
                        }
                    } ?: run {
                        Timber.e("[Mobile] ERROR [Backup] 查询时间范围 ${range.first} 到 ${range.second} 失败: 返回null")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[Mobile] ERROR [Backup] 查询时间范围 ${range.first} 到 ${range.second} 失败: ${e.message}")
                    // 继续查询下一个时间范围
                }
            }
            
            Timber.d("[Mobile] DEBUG [Backup] 找到 ${callLogs.size} 条通话记录，总查询数 $totalCallLogs")
            Timber.i("[Mobile] INFO [Backup] 成功读取 ${callLogs.size} 条通话记录")
            
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Backup] 备份通话记录异常: ${e.message}")
            return callLogs.takeIf { it.isNotEmpty() } // 返回已收集的记录，而不是null
        }
        
        return callLogs
    }
    
    /**
     * 生成时间范围用于分批查询数据
     * 
     * @param ranges 时间区间数量，默认为4
     * @return 时间范围列表，每个元素为一个开始时间和结束时间的对
     */
    private fun generateTimeRanges(ranges: Int = 4): List<Pair<Long, Long>> {
        val result = mutableListOf<Pair<Long, Long>>()
        val endTime = System.currentTimeMillis()
        // 默认查询最近1年的记录
        val startTime = endTime - (365L * 24 * 60 * 60 * 1000)
        
        val rangeSize = (endTime - startTime) / ranges
        
        for (i in 0 until ranges) {
            val rangeStart = startTime + (i * rangeSize)
            val rangeEnd = if (i == ranges - 1) endTime else startTime + ((i + 1) * rangeSize - 1)
            result.add(Pair(rangeStart, rangeEnd))
        }
        
        return result
    }
    
    /**
     * 读取联系人
     * 增强版：读取更多联系人信息，包括电子邮件、地址等
     */
    private fun readContacts(): List<Contact>? {
        val contacts = mutableListOf<Contact>()
        
        try {
            // 检查权限
            val permissionStatus = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
            if (permissionStatus != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Timber.e("[Mobile] ERROR [Backup] 备份联系人失败: 没有 READ_CONTACTS 权限")
                return null
            }
            
            Timber.d("[Mobile] DEBUG [Backup] 开始读取联系人...")
            
            // 查询联系人
            val uri = android.provider.ContactsContract.Contacts.CONTENT_URI
            val projection = arrayOf(
                android.provider.ContactsContract.Contacts._ID,
                android.provider.ContactsContract.Contacts.DISPLAY_NAME,
                android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER,
                android.provider.ContactsContract.Contacts.PHOTO_URI
            )
            val sortOrder = "${android.provider.ContactsContract.Contacts.DISPLAY_NAME} ASC"
            
            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                Timber.d("[Mobile] DEBUG [Backup] 找到 ${cursor.count} 个联系人")
                
                val idColumn = cursor.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                val nameColumn = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneColumn = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val photoUriColumn = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.PHOTO_URI)
                
                // 分批处理联系人，每批次最多50个
                val batchSize = 50
                var contactsProcessed = 0
                
                while (cursor.moveToNext()) {
                    try {
                        val id = if (idColumn != -1) cursor.getLong(idColumn) else 0
                        val name = if (nameColumn != -1) cursor.getString(nameColumn) else ""
                        val hasPhone = if (hasPhoneColumn != -1) cursor.getInt(hasPhoneColumn) > 0 else false
                        val photoUri = if (photoUriColumn != -1) cursor.getString(photoUriColumn) else null
                        
                        val phoneNumbers = mutableListOf<String>()
                        val emails = mutableListOf<String>()
                        val addresses = mutableListOf<Contact.Address>()
                        val websites = mutableListOf<String>()
                        val socialProfiles = mutableListOf<Contact.SocialProfile>()
                        val relationships = mutableListOf<Contact.Relationship>()
                        var note: String? = null
                        val groups = mutableListOf<String>()
                        val events = mutableListOf<Contact.Event>()
                        
                        // 1. 读取电话号码
                        if (hasPhone) {
                            val phoneUri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                            val phoneProjection = arrayOf(
                                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                                android.provider.ContactsContract.CommonDataKinds.Phone.TYPE
                            )
                            val phoneSelection = "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
                            val phoneSelectionArgs = arrayOf(id.toString())
                            
                            context.contentResolver.query(phoneUri, phoneProjection, phoneSelection, phoneSelectionArgs, null)?.use { phoneCursor ->
                                val phoneNumberColumn = phoneCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                                val phoneTypeColumn = phoneCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE)
                                
                                while (phoneCursor.moveToNext()) {
                                    val phoneNumber = if (phoneNumberColumn != -1) phoneCursor.getString(phoneNumberColumn) else ""
                                    val phoneType = if (phoneTypeColumn != -1) {
                                        when (phoneCursor.getInt(phoneTypeColumn)) {
                                            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "家庭"
                                            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "手机"
                                            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "工作"
                                            else -> "其他"
                                        }
                                    } else "其他"
                                    
                                    if (phoneNumber.isNotBlank()) {
                                        // 存储电话号码和类型信息
                                        phoneNumbers.add(phoneNumber)
                                    }
                                }
                            }
                        }
                        
                        // 2. 读取电子邮件
                        val emailUri = android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI
                        val emailProjection = arrayOf(
                            android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS,
                            android.provider.ContactsContract.CommonDataKinds.Email.TYPE
                        )
                        val emailSelection = "${android.provider.ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?"
                        val emailSelectionArgs = arrayOf(id.toString())
                        
                        context.contentResolver.query(emailUri, emailProjection, emailSelection, emailSelectionArgs, null)?.use { emailCursor ->
                            val emailAddressColumn = emailCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS)
                            val emailTypeColumn = emailCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Email.TYPE)
                            
                            while (emailCursor.moveToNext()) {
                                val emailAddress = if (emailAddressColumn != -1) emailCursor.getString(emailAddressColumn) else ""
                                val emailType = if (emailTypeColumn != -1) {
                                    when (emailCursor.getInt(emailTypeColumn)) {
                                        android.provider.ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "家庭"
                                        android.provider.ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "工作"
                                        else -> "其他"
                                    }
                                } else "其他"
                                
                                if (emailAddress.isNotBlank()) {
                                    emails.add(emailAddress)
                                }
                            }
                        }
                        
                        // 3. 读取地址信息
                        val addressUri = android.provider.ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI
                        val addressProjection = arrayOf(
                            android.provider.ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                            android.provider.ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                            android.provider.ContactsContract.CommonDataKinds.StructuredPostal.STREET,
                            android.provider.ContactsContract.CommonDataKinds.StructuredPostal.CITY,
                            android.provider.ContactsContract.CommonDataKinds.StructuredPostal.REGION,
                            android.provider.ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
                            android.provider.ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY
                        )
                        val addressSelection = "${android.provider.ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID} = ?"
                        val addressSelectionArgs = arrayOf(id.toString())
                        
                        context.contentResolver.query(addressUri, addressProjection, addressSelection, addressSelectionArgs, null)?.use { addressCursor ->
                            val addressColumn = addressCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                            val addressTypeColumn = addressCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.StructuredPostal.TYPE)
                            
                            while (addressCursor.moveToNext()) {
                                val address = if (addressColumn != -1) addressCursor.getString(addressColumn) else ""
                                val addressType = if (addressTypeColumn != -1) {
                                    val type = addressCursor.getInt(addressTypeColumn)
                                    when (type) {
                                        android.provider.ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> "家庭"
                                        android.provider.ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> "工作"
                                        android.provider.ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER -> "其他"
                                        else -> "其他"
                                    }
                                } else "其他"
                                
                                if (address.isNotBlank()) {
                                    addresses.add(Contact.Address(addressType, address))
                                }
                            }
                        }
                        
                        // 4. 读取备注
                        val noteUri = android.provider.ContactsContract.Data.CONTENT_URI
                        val noteProjection = arrayOf(
                            android.provider.ContactsContract.CommonDataKinds.Note.NOTE
                        )
                        val noteSelection = "${android.provider.ContactsContract.Data.CONTACT_ID} = ? AND ${android.provider.ContactsContract.Data.MIMETYPE} = ?"
                        val noteSelectionArgs = arrayOf(
                            id.toString(),
                            android.provider.ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                        )
                        
                        context.contentResolver.query(noteUri, noteProjection, noteSelection, noteSelectionArgs, null)?.use { noteCursor ->
                            val noteColumn = noteCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Note.NOTE)
                            
                            if (noteCursor.moveToFirst()) {
                                note = if (noteColumn != -1) noteCursor.getString(noteColumn) else null
                            }
                        }
                        
                        // 5. 读取网站
                        val websiteUri = android.provider.ContactsContract.Data.CONTENT_URI
                        val websiteProjection = arrayOf(
                            android.provider.ContactsContract.CommonDataKinds.Website.URL
                        )
                        val websiteSelection = "${android.provider.ContactsContract.Data.CONTACT_ID} = ? AND ${android.provider.ContactsContract.Data.MIMETYPE} = ?"
                        val websiteSelectionArgs = arrayOf(
                            id.toString(),
                            android.provider.ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE
                        )
                        
                        context.contentResolver.query(websiteUri, websiteProjection, websiteSelection, websiteSelectionArgs, null)?.use { websiteCursor ->
                            val urlColumn = websiteCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Website.URL)
                            
                            while (websiteCursor.moveToNext()) {
                                val url = if (urlColumn != -1) websiteCursor.getString(urlColumn) else ""
                                if (url.isNotBlank()) {
                                    websites.add(url)
                                }
                            }
                        }
                        
                        // 6. 读取事件（如生日、纪念日等）
                        val eventUri = android.provider.ContactsContract.Data.CONTENT_URI
                        val eventProjection = arrayOf(
                            android.provider.ContactsContract.CommonDataKinds.Event.START_DATE,
                            android.provider.ContactsContract.CommonDataKinds.Event.TYPE
                        )
                        val eventSelection = "${android.provider.ContactsContract.Data.CONTACT_ID} = ? AND ${android.provider.ContactsContract.Data.MIMETYPE} = ?"
                        val eventSelectionArgs = arrayOf(
                            id.toString(),
                            android.provider.ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
                        )
                        
                        context.contentResolver.query(eventUri, eventProjection, eventSelection, eventSelectionArgs, null)?.use { eventCursor ->
                            val dateColumn = eventCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Event.START_DATE)
                            val typeColumn = eventCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Event.TYPE)
                            
                            while (eventCursor.moveToNext()) {
                                val date = if (dateColumn != -1) eventCursor.getString(dateColumn) else ""
                                val type = if (typeColumn != -1) {
                                    when (eventCursor.getInt(typeColumn)) {
                                        android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> "生日"
                                        android.provider.ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> "纪念日"
                                        else -> "其他"
                                    }
                                } else "其他"
                                
                                if (date.isNotBlank()) {
                                    events.add(Contact.Event(type, date))
                                }
                            }
                        }
                        
                        // 7. 读取所属群组
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val groupUri = android.provider.ContactsContract.Data.CONTENT_URI
                            val groupProjection = arrayOf(
                                android.provider.ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID
                            )
                            val groupSelection = "${android.provider.ContactsContract.Data.CONTACT_ID} = ? AND ${android.provider.ContactsContract.Data.MIMETYPE} = ?"
                            val groupSelectionArgs = arrayOf(
                                id.toString(),
                                android.provider.ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
                            )
                            
                            context.contentResolver.query(groupUri, groupProjection, groupSelection, groupSelectionArgs, null)?.use { groupCursor ->
                                val groupIdColumn = groupCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)
                                
                                while (groupCursor.moveToNext()) {
                                    val groupId = if (groupIdColumn != -1) groupCursor.getLong(groupIdColumn) else -1
                                    
                                    if (groupId != -1L) {
                                        // 根据组ID查询组名称
                                        val groupNameUri = android.provider.ContactsContract.Groups.CONTENT_URI
                                        val groupNameProjection = arrayOf(
                                            android.provider.ContactsContract.Groups.TITLE
                                        )
                                        val groupNameSelection = "${android.provider.ContactsContract.Groups._ID} = ?"
                                        val groupNameSelectionArgs = arrayOf(groupId.toString())
                                        
                                        context.contentResolver.query(groupNameUri, groupNameProjection, groupNameSelection, groupNameSelectionArgs, null)?.use { groupNameCursor ->
                                            val groupNameColumn = groupNameCursor.getColumnIndex(android.provider.ContactsContract.Groups.TITLE)
                                            
                                            if (groupNameCursor.moveToFirst()) {
                                                val groupName = if (groupNameColumn != -1) groupNameCursor.getString(groupNameColumn) else ""
                                                if (groupName.isNotBlank()) {
                                                    groups.add(groupName)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 8. 读取关系（如配偶、子女等）
                        val relationUri = android.provider.ContactsContract.Data.CONTENT_URI
                        val relationProjection = arrayOf(
                            android.provider.ContactsContract.CommonDataKinds.Relation.NAME,
                            android.provider.ContactsContract.CommonDataKinds.Relation.TYPE
                        )
                        val relationSelection = "${android.provider.ContactsContract.Data.CONTACT_ID} = ? AND ${android.provider.ContactsContract.Data.MIMETYPE} = ?"
                        val relationSelectionArgs = arrayOf(
                            id.toString(),
                            android.provider.ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE
                        )
                        
                        context.contentResolver.query(relationUri, relationProjection, relationSelection, relationSelectionArgs, null)?.use { relationCursor ->
                            val nameColumn = relationCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Relation.NAME)
                            val typeColumn = relationCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE)
                            
                            while (relationCursor.moveToNext()) {
                                val relName = if (nameColumn != -1) relationCursor.getString(nameColumn) else ""
                                val relType = if (typeColumn != -1) {
                                    when (relationCursor.getInt(typeColumn)) {
                                        android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_SPOUSE -> "配偶"
                                        android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_CHILD -> "子女"
                                        android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_PARENT -> "父母"
                                        else -> "其他"
                                    }
                                } else "其他"
                                
                                if (relName.isNotBlank()) {
                                    relationships.add(Contact.Relationship(relType, relName))
                                }
                            }
                        }
                        
                        // 9. 读取社交资料
                        val socialUri = android.provider.ContactsContract.Data.CONTENT_URI
                        val socialProjection = arrayOf(
                            android.provider.ContactsContract.Data.MIMETYPE,
                            android.provider.ContactsContract.Data.DATA1,
                            android.provider.ContactsContract.Data.DATA2
                        )
                        val socialSelection = "${android.provider.ContactsContract.Data.CONTACT_ID} = ? AND ${android.provider.ContactsContract.Data.MIMETYPE} IN (?, ?, ?)"
                        val socialSelectionArgs = arrayOf(
                            id.toString(),
                            "vnd.android.cursor.item/com.whatsapp.profile",
                            "vnd.android.cursor.item/com.facebook.profile",
                            "vnd.android.cursor.item/com.twitter.android.profile"
                        )
                        
                        context.contentResolver.query(socialUri, socialProjection, socialSelection, socialSelectionArgs, null)?.use { socialCursor ->
                            val mimeTypeColumn = socialCursor.getColumnIndex(android.provider.ContactsContract.Data.MIMETYPE)
                            val data1Column = socialCursor.getColumnIndex(android.provider.ContactsContract.Data.DATA1)
                            
                            while (socialCursor.moveToNext()) {
                                val mimeType = if (mimeTypeColumn != -1) socialCursor.getString(mimeTypeColumn) else ""
                                val data = if (data1Column != -1) socialCursor.getString(data1Column) else ""
                                
                                if (data.isNotBlank()) {
                                    // 根据MIME类型确定社交媒体类型
                                    val socialType = when {
                                        mimeType.contains("whatsapp") -> "WhatsApp"
                                        mimeType.contains("facebook") -> "Facebook"
                                        mimeType.contains("twitter") -> "Twitter"
                                        else -> "其他"
                                    }
                                    
                                    socialProfiles.add(Contact.SocialProfile(socialType, data))
                                }
                            }
                        }
                        
                        if (name.isNotBlank() || phoneNumbers.isNotEmpty() || emails.isNotEmpty()) {
                            val contact = Contact(
                                id = id,
                                name = name,
                                phoneNumbers = phoneNumbers,
                                emails = if (emails.isNotEmpty()) emails else null,
                                addresses = if (addresses.isNotEmpty()) addresses else null,
                                note = note,
                                groups = if (groups.isNotEmpty()) groups else null,
                                websites = if (websites.isNotEmpty()) websites else null,
                                events = if (events.isNotEmpty()) events else null,
                                relationships = if (relationships.isNotEmpty()) relationships else null,
                                socialProfiles = if (socialProfiles.isNotEmpty()) socialProfiles else null
                            )
                            contacts.add(contact)
                        }
                        
                        // 每处理50个联系人，记录一下进度
                        contactsProcessed++
                        if (contactsProcessed % batchSize == 0) {
                            Timber.d("[Mobile] DEBUG [Backup] 已处理 $contactsProcessed/${cursor.count} 个联系人")
                        }
                        
                    } catch (e: Exception) {
                        Timber.e(e, "[Mobile] ERROR [Backup] 处理单个联系人时出错: ${e.message}")
                        // 继续处理下一个联系人
                    }
                }
            } ?: run {
                Timber.e("[Mobile] ERROR [Backup] 备份联系人失败: 无法查询联系人内容提供者")
                return null
            }
            
            Timber.i("[Mobile] INFO [Backup] 成功读取 ${contacts.size} 个联系人")
            
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Backup] 备份联系人异常: ${e.message}")
            return null
        }
        
        return contacts
    }
    
    /**
     * 备份结果数据类
     */
    data class BackupResult(
        val timestamp: Date,
        val messagesCount: Int = 0,
        val callLogsCount: Int = 0,
        val contactsCount: Int = 0,
        val backupFilePath: String? = null,
        val errorMessage: String? = null
    ) {
        val isSuccess: Boolean
            get() = backupFilePath != null && errorMessage == null
    }
}
