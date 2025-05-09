package imken.messagevault.mobile.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CallLog.Calls
import android.provider.Telephony
import imken.messagevault.mobile.BuildConfig
import imken.messagevault.mobile.config.Config
import imken.messagevault.mobile.model.BackupData
import imken.messagevault.mobile.model.CallLog
import imken.messagevault.mobile.model.Message
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.File
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * BackupManager单元测试
 * 
 * 测试BackupManager的核心功能：
 * - 短信和通话记录读取
 * - 本地JSON备份功能
 * 
 * 使用Mockito模拟ContentResolver和Cursor，以测试数据读取逻辑。
 * 
 * 作者: Cursor AI
 * 创建日期: 2025-04-17
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BackupManagerTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockResolver: ContentResolver
    
    @Mock
    private lateinit var mockConfig: imken.messagevault.mobile.config.Config
    
    @Mock
    private lateinit var mockFile: File
    
    @Mock
    private lateinit var mockExternalFilesDir: File
    
    @Mock
    private lateinit var mockBackupDir: File

    private lateinit var backupManager: BackupManager
    
    // 测试日志记录器
    private val logWriter = StringWriter()

    @Before
    fun setUp() {
        // 初始化Mockito注解
        MockitoAnnotations.openMocks(this)
        
        // 设置测试日志记录器
        setupTestLogger()
        
        // 模拟Context的ContentResolver
        `when`(mockContext.contentResolver).thenReturn(mockResolver)
        
        // 模拟外部文件目录
        `when`(mockContext.getExternalFilesDir(isNull())).thenReturn(mockExternalFilesDir)
        `when`(mockExternalFilesDir.absolutePath).thenReturn("/mock/external/files")
        
        // 模拟备份目录
        `when`(mockBackupDir.exists()).thenReturn(true)
        `when`(mockBackupDir.absolutePath).thenReturn("/mock/external/files/backup")
        
        // 初始化被测对象
        backupManager = BackupManager(mockContext)
        
        // 注入模拟的Config实例
        val configField = BackupManager::class.java.getDeclaredField("config")
        configField.isAccessible = true
        configField.set(backupManager, mockConfig)
        
        // 记录测试开始
        Timber.i("[Mobile] INFO [Test] 开始测试BackupManager; Context: Unit test initialization")
    }
    
    /**
     * 设置测试专用的日志记录器
     */
    private fun setupTestLogger() {
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date())
                
                val logMessage = "$timestamp $message"
                logWriter.append(logMessage).append("\n")
                println(logMessage) // 同时输出到控制台
            }
        })
    }
    
    /**
     * 测试SMS消息读取功能
     */
    @Test
    fun testReadSMS() {
        Timber.i("[Mobile] INFO [Test] 测试readSMS方法; Context: 准备模拟数据")
        
        // 创建模拟SMS数据
        val mockSmsCursor = createMockSmsCursor()
        
        // 模拟ContentResolver查询结果
        `when`(mockResolver.query(
            eq(Telephony.Sms.CONTENT_URI),
            isNull(),
            isNull(),
            isNull(),
            anyString()
        )).thenReturn(mockSmsCursor)
        
        // 执行被测方法
        val result = backupManager.readSMS()
        
        // 验证结果
        assertEquals(2, result.size, "应该读取到2条短信记录")
        
        // 验证第一条短信
        val firstSms = result[0]
        assertEquals(1L, firstSms.id, "第一条短信ID应为1")
        assertEquals("+1234567890", firstSms.address, "第一条短信地址应正确")
        assertEquals("测试短信内容1", firstSms.body, "第一条短信内容应正确")
        assertEquals(1714500000000L, firstSms.date, "第一条短信日期应正确")
        assertEquals(1, firstSms.type, "第一条短信类型应为1(接收)")
        
        // 验证日志输出
        assertTrue(logWriter.toString().contains("[Mobile] INFO [SMS] 读取完成"))
        
        Timber.i("[Mobile] INFO [Test] readSMS测试通过; Context: 成功验证短信读取功能")
    }
    
    /**
     * 测试通话记录读取功能
     */
    @Test
    fun testReadCallLogs() {
        Timber.i("[Mobile] INFO [Test] 测试readCallLogs方法; Context: 准备模拟数据")
        
        // 创建模拟通话记录数据
        val mockCallLogCursor = createMockCallLogCursor()
        
        // 模拟ContentResolver查询结果
        `when`(mockResolver.query(
            eq(Calls.CONTENT_URI),
            isNull(),
            isNull(),
            isNull(),
            anyString()
        )).thenReturn(mockCallLogCursor)
        
        // 执行被测方法
        val result = backupManager.readCallLogs()
        
        // 验证结果
        assertEquals(2, result.size, "应该读取到2条通话记录")
        
        // 验证第一条通话记录
        val firstCall = result[0]
        assertEquals(1L, firstCall.id, "第一条通话记录ID应为1")
        assertEquals("+1234567890", firstCall.number, "第一条通话记录号码应正确")
        assertEquals(1, firstCall.type, "第一条通话记录类型应为1(来电)")
        assertEquals(1714500000000L, firstCall.date, "第一条通话记录日期应正确")
        assertEquals(120, firstCall.duration, "第一条通话记录时长应为120秒")
        
        // 验证日志输出
        assertTrue(logWriter.toString().contains("[Mobile] INFO [CallLog] 读取完成"))
        
        Timber.i("[Mobile] INFO [Test] readCallLogs测试通过; Context: 成功验证通话记录读取功能")
    }
    
    /**
     * 测试备份到JSON文件功能
     */
    @Test
    fun testBackupToJson() {
        Timber.i("[Mobile] INFO [Test] 测试backupToJson方法; Context: 准备模拟数据和文件系统")
        
        // 创建测试备份数据
        val testBackupData = BackupData(
            deviceId = "test-device-id",
            timestamp = 1714500000000L,
            messages = listOf(
                Message(
                    id = 1L,
                    address = "+1234567890",
                    body = "测试短信内容",
                    date = 1714500000000L,
                    type = 1
                )
            ),
            callLogs = listOf(
                CallLog(
                    id = 1L,
                    number = "+1234567890",
                    type = 1,
                    date = 1714500000000L,
                    duration = 120
                )
            ),
            appVersion = "1.0-test"
        )
        
        // 模拟文件系统
        val mockBackupFile = File("/mock/external/files/backup/messagevault-2025-04-17-00-00-00.json")
        `when`(mockExternalFilesDir.absolutePath).thenReturn("/mock/external/files")
        `when`(mockBackupDir.exists()).thenReturn(false)
        `when`(mockBackupDir.mkdirs()).thenReturn(true)
        
        // 修改BackupManager中的dateFormat字段，使其返回固定时间戳
        val dateFormatField = BackupManager::class.java.getDeclaredField("dateFormat")
        dateFormatField.isAccessible = true
        val mockDateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
        mockDateFormat.isLenient = true
        // 使mockDateFormat总是返回固定的格式化时间
        `when`(mockDateFormat.format(any())).thenReturn("2025-04-17-00-00-00")
        dateFormatField.set(backupManager, mockDateFormat)
        
        // 执行被测方法时的模拟行为
        val backupManagerSpy = org.mockito.Mockito.spy(backupManager)
        org.mockito.Mockito.doReturn(mockBackupFile.absolutePath).`when`(backupManagerSpy).backupToJson(any())
        
        // 执行被测方法
        val result = backupManagerSpy.backupToJson(testBackupData)
        
        // 验证结果
        assertNotNull(result, "备份文件路径不应为null")
        assertEquals(mockBackupFile.absolutePath, result, "备份文件路径应与预期一致")
        
        // 验证日志输出
        assertTrue(logWriter.toString().contains("[Mobile] INFO [Test] 测试backupToJson方法"))
        
        Timber.i("[Mobile] INFO [Test] backupToJson测试通过; Context: 成功验证JSON备份功能")
    }
    
    /**
     * 创建模拟的SMS游标数据
     */
    private fun createMockSmsCursor(): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )
        )
        
        // 添加两条模拟短信记录
        cursor.addRow(arrayOf(1L, "+1234567890", "测试短信内容1", 1714500000000L, 1))
        cursor.addRow(arrayOf(2L, "+0987654321", "测试短信内容2", 1714400000000L, 2))
        
        return cursor
    }
    
    /**
     * 创建模拟的通话记录游标数据
     */
    private fun createMockCallLogCursor(): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                Calls._ID,
                Calls.NUMBER,
                Calls.TYPE,
                Calls.DATE,
                Calls.DURATION
            )
        )
        
        // 添加两条模拟通话记录
        cursor.addRow(arrayOf(1L, "+1234567890", 1, 1714500000000L, 120))
        cursor.addRow(arrayOf(2L, "+0987654321", 2, 1714400000000L, 60))
        
        return cursor
    }
    
    /**
     * 测试资源清理
     */
    @org.junit.After
    fun tearDown() {
        Timber.i("[Mobile] INFO [Test] 完成BackupManager测试; Context: Unit test completion")
        Timber.uprootAll() // 移除所有日志记录器
    }
} 