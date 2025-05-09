package imken.messagevault.mobile.data

import android.content.Context
import android.os.Build
import timber.log.Timber
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.io.File
import com.google.gson.Gson
import imken.messagevault.mobile.BuildConfig
import imken.messagevault.mobile.model.BackupData
import android.provider.Settings
import java.util.UUID

/**
 * 备份管理器
 * 
 * 负责执行备份操作，包括短信、通话记录和联系人的备份
 */
class BackupManager(private val context: Context) {
    
    private val gson = Gson()
    
    /**
     * 执行备份操作
     * 
     * @return 备份结果，包含备份的短信、通话记录和联系人数量
     */
    suspend fun performBackup(): BackupResult {
        Timber.i("[Mobile] INFO [Backup] 开始备份操作")
        
        // 创建备份数据
        val backupData = createBackupData()
        
        // 保存到文件
        val filePath = backupToJson(backupData)
        
        Timber.i("[Mobile] INFO [Backup] 备份完成: 短信(${backupData.messages?.size ?: 0}), 通话记录(${backupData.callLogs?.size ?: 0}), 联系人(${backupData.contacts?.size ?: 0})")
        
        return BackupResult(
            timestamp = Date(),
            messagesCount = backupData.messages?.size ?: 0,
            callLogsCount = backupData.callLogs?.size ?: 0,
            contactsCount = backupData.contacts?.size ?: 0,
            backupFilePath = filePath
        )
    }
    
    /**
     * 创建备份数据
     * 
     * @return 包含短信、通话记录和联系人的备份数据对象
     */
    fun createBackupData(): BackupData {
        // 从设备读取短信
        val messages = readMessages()
        
        // 从设备读取通话记录
        val callLogs = readCallLogs()
        
        // 从设备读取联系人
        val contacts = readContacts()
        
        // 创建备份数据对象
        return BackupData(
            messages = messages,
            callLogs = callLogs,
            contacts = contacts,
            timestamp = System.currentTimeMillis(),
            deviceInfo = getDeviceInfo()
        )
    }
    
    /**
     * 生成备份文件名
     * 
     * 使用日期时间和设备型号生成易读的备份文件名
     * 
     * @return 生成的备份文件名
     */
    private fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val dateTime = dateFormat.format(Date())
        val deviceModel = getDeviceModelName()
        
        return "MessageVault_${dateTime}_${deviceModel}.json"
    }
    
    /**
     * 获取设备型号名称（简化版）
     * 
     * @return 设备型号名称
     */
    private fun getDeviceModelName(): String {
        // 获取设备型号，并删除特殊字符
        val model = Build.MODEL.replace("[^a-zA-Z0-9]".toRegex(), "")
        return if (model.length > 10) model.substring(0, 10) else model
    }
    
    /**
     * 获取设备信息
     * 
     * @return 设备信息字符串
     */
    private fun getDeviceInfo(): String {
        return "设备: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
               "Android版本: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
               "应用版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }
    
    /**
     * 将备份数据保存为JSON文件
     * 
     * @param backupData 备份数据对象
     * @return 保存的文件路径
     */
    fun backupToJson(backupData: BackupData): String {
            // 确保备份目录存在
        val backupDir = File(context.getExternalFilesDir(null), "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
        }
        
        // 生成备份文件名
        val fileName = generateBackupFileName()
        val backupFile = File(backupDir, fileName)
        
        // 将备份数据序列化为JSON并写入文件
        val json = gson.toJson(backupData)
        backupFile.writeText(json)
        
        Timber.i("[Mobile] INFO [Backup] 备份文件已保存: ${backupFile.absolutePath}")
        
        return backupFile.absolutePath
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
            
            // 查询短信
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
            
            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                Timber.d("[Mobile] DEBUG [Backup] 找到 ${cursor.count} 条通话记录")
                
                val idColumn = cursor.getColumnIndex(android.provider.CallLog.Calls._ID)
                val numberColumn = cursor.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                val nameColumn = cursor.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
                val dateColumn = cursor.getColumnIndex(android.provider.CallLog.Calls.DATE)
                val durationColumn = cursor.getColumnIndex(android.provider.CallLog.Calls.DURATION)
                val typeColumn = cursor.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                
                while (cursor.moveToNext()) {
                    val id = if (idColumn != -1) cursor.getLong(idColumn) else 0
                    val number = if (numberColumn != -1) cursor.getString(numberColumn) else ""
                    val name = if (nameColumn != -1) cursor.getString(nameColumn) else null
                    val date = if (dateColumn != -1) cursor.getLong(dateColumn) else 0
                    val duration = if (durationColumn != -1) cursor.getInt(durationColumn) else 0
                    val type = if (typeColumn != -1) cursor.getInt(typeColumn) else 0
                    
                    val callLog = imken.messagevault.mobile.model.CallLog(
                        id = id,
                        number = number,
                        contact = name,
                        date = date,
                        duration = duration,
                        type = type
                    )
                    
                    callLogs.add(callLog)
                }
            } ?: run {
                Timber.e("[Mobile] ERROR [Backup] 备份通话记录失败: 无法查询通话记录内容提供者")
                return null
            }
            
            Timber.i("[Mobile] INFO [Backup] 成功读取 ${callLogs.size} 条通话记录")
            
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Backup] 备份通话记录异常: ${e.message}")
            return null
        }
        
        return callLogs
    }
    
    private fun readContacts(): List<imken.messagevault.mobile.model.Contact>? {
        val contacts = mutableListOf<imken.messagevault.mobile.model.Contact>()
        
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
                android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER
            )
            val sortOrder = "${android.provider.ContactsContract.Contacts.DISPLAY_NAME} ASC"
            
            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                Timber.d("[Mobile] DEBUG [Backup] 找到 ${cursor.count} 个联系人")
                
                val idColumn = cursor.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                val nameColumn = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneColumn = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER)
                
                while (cursor.moveToNext()) {
                    val id = if (idColumn != -1) cursor.getLong(idColumn) else 0
                    val name = if (nameColumn != -1) cursor.getString(nameColumn) else ""
                    val hasPhone = if (hasPhoneColumn != -1) cursor.getInt(hasPhoneColumn) > 0 else false
                    
                    val phoneNumbers = mutableListOf<String>()
                    
                    // 如果有电话号码，查询电话号码
                    if (hasPhone) {
                        val phoneUri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                        val phoneProjection = arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val phoneSelection = "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
                        val phoneSelectionArgs = arrayOf(id.toString())
                        
                        context.contentResolver.query(phoneUri, phoneProjection, phoneSelection, phoneSelectionArgs, null)?.use { phoneCursor ->
                            val phoneNumberColumn = phoneCursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                            
                            while (phoneCursor.moveToNext()) {
                                val phoneNumber = if (phoneNumberColumn != -1) phoneCursor.getString(phoneNumberColumn) else ""
                                if (phoneNumber.isNotBlank()) {
                                    phoneNumbers.add(phoneNumber)
                                }
                            }
                        }
                    }
                    
                    if (name.isNotBlank() || phoneNumbers.isNotEmpty()) {
                        val contact = imken.messagevault.mobile.model.Contact(
                            id = id,
                            name = name,
                            phoneNumbers = phoneNumbers
                        )
                        contacts.add(contact)
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
        val backupFilePath: String? = null
    )
}
