package imken.messagevault.mobile

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import imken.messagevault.mobile.data.BackupManager
import imken.messagevault.mobile.model.BackupData
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * BackupManager设备测试
 * 
 * 在真实设备或模拟器上测试以下功能：
 * - 实际环境中的短信和通话记录读取
 * - 备份文件创建和内容验证
 * 
 * 此测试需要授予READ_SMS和READ_CALL_LOG权限。
 * 
 * 作者: Cursor AI
 * 创建日期: 2025-04-17
 */
@RunWith(AndroidJUnit4::class)
class BackupManagerInstrumentedTest {

    // 自动授予必要权限
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    
    private lateinit var context: Context
    private lateinit var backupManager: BackupManager
    private var testStartTime: Long = 0

    @Before
    fun setUp() {
        // 获取应用上下文
        context = ApplicationProvider.getApplicationContext()
        
        // 设置测试日志记录器
        setupTestLogger()
        
        // 获取测试开始时间
        testStartTime = System.currentTimeMillis()
        
        // 初始化被测对象
        backupManager = BackupManager(context)
        
        // 记录测试开始
        Timber.i("[Mobile] INFO [Test] 开始BackupManager设备测试; Context: Instrumented test on device")
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
                
                // 构造日志消息 
                val logMessage = "$timestamp $message"
                
                // 输出到控制台和Logcat
                println(logMessage)
                android.util.Log.println(priority, "MessageVaultTest", logMessage)
            }
        })
    }

    /**
     * 测试短信读取功能
     * 
     * 注意：此测试需要设备上有短信数据，否则将通过但没有实际内容验证
     */
    @Test
    fun testReadSMS() {
        // 执行被测方法
        val messages = backupManager.readSMS()
        
        // 记录结果
        Timber.i("[Mobile] INFO [Test] 读取到 ${messages.size} 条短信; Context: Instrumented test on device")
        
        // 验证返回的列表不为null
        assertNotNull(messages, "短信列表不应为null")
        
        // 如果有短信，检查第一条短信的内容
        if (messages.isNotEmpty()) {
            val firstSms = messages[0]
            assertNotNull(firstSms.address, "短信地址不应为null")
            assertNotNull(firstSms.body, "短信内容不应为null")
            assertTrue(firstSms.id > 0, "短信ID应大于0")
            
            // 记录第一条短信内容摘要（不记录完整内容以保护隐私）
            Timber.i("[Mobile] INFO [Test] 首条短信摘要; Context: ID=${firstSms.id}, 地址长度=${firstSms.address.length}, 内容长度=${firstSms.body.length}")
        }
        
        Timber.i("[Mobile] INFO [Test] readSMS测试通过; Context: 成功在设备上读取短信数据")
    }

    /**
     * 测试通话记录读取功能
     * 
     * 注意：此测试需要设备上有通话记录数据，否则将通过但没有实际内容验证
     */
    @Test
    fun testReadCallLogs() {
        // 执行被测方法 
        val callLogs = backupManager.readCallLogs()
        
        // 记录结果
        Timber.i("[Mobile] INFO [Test] 读取到 ${callLogs.size} 条通话记录; Context: Instrumented test on device")
        
        // 验证返回的列表不为null
        assertNotNull(callLogs, "通话记录列表不应为null")
        
        // 如果有通话记录，检查第一条通话记录的内容
        if (callLogs.isNotEmpty()) {
            val firstCallLog = callLogs[0]
            assertNotNull(firstCallLog.number, "通话号码不应为null")
            assertTrue(firstCallLog.id > 0, "通话记录ID应大于0")
            assertTrue(firstCallLog.date > 0, "通话日期应大于0")
            
            // 记录第一条通话记录内容摘要
            Timber.i("[Mobile] INFO [Test] 首条通话记录摘要; Context: ID=${firstCallLog.id}, 类型=${firstCallLog.type}, 时长=${firstCallLog.duration}秒")
        }
        
        Timber.i("[Mobile] INFO [Test] readCallLogs测试通过; Context: 成功在设备上读取通话记录数据")
    }

    /**
     * 测试备份到JSON文件功能
     */
    @Test
    fun testBackupToJson() {
        // 创建BackupData对象
        val backupData = backupManager.createBackupData()
        
        // 记录备份数据信息
        Timber.i("[Mobile] INFO [Test] 创建备份数据; Context: 短信数量=${backupData.messages.size}, 通话记录数量=${backupData.callLogs.size}")
        
        // 执行备份
        val filePath = backupManager.backupToJson(backupData)
        
        // 验证文件路径
        assertNotNull(filePath, "备份文件路径不应为null")
        
        // 验证文件是否实际创建
        val backupFile = File(filePath)
        assertTrue(backupFile.exists(), "备份文件应该存在")
        assertTrue(backupFile.length() > 0, "备份文件不应为空")
        
        // 记录文件信息
        Timber.i("[Mobile] INFO [Test] 创建备份文件; Context: 文件路径=$filePath, 文件大小=${backupFile.length()}字节")
        
        // 清理测试生成的文件
        backupFile.delete()
        
        Timber.i("[Mobile] INFO [Test] backupToJson测试通过; Context: 成功在设备上创建备份文件")
    }

    /**
     * 测试完整备份流程
     */
    @Test
    fun testExecuteBackup() {
        // 记录测试开始
        Timber.i("[Mobile] INFO [Test] 测试完整备份流程; Context: Instrumented test on device")
        
        // 创建测试回调
        var callbackResult: Boolean? = null
        var callbackMessage: String? = null
        
        // 执行备份（只本地备份，不上传）
        backupManager.executeBackup(false) { success, message ->
            callbackResult = success
            callbackMessage = message
            
            // 记录回调结果
            Timber.i("[Mobile] INFO [Test] 备份回调; Context: success=$success, message=$message")
        }
        
        // 等待异步操作完成
        Thread.sleep(2000)
        
        // 验证回调结果
        assertNotNull(callbackResult, "回调结果不应为null")
        assertNotNull(callbackMessage, "回调消息不应为null")
        assertEquals(true, callbackResult, "备份应成功完成")
        
        // 获取备份文件路径
        val filePath = callbackMessage?.let {
            if (it.startsWith("本地备份成功:")) {
                it.substringAfter("本地备份成功:").trim()
            } else {
                it.substringAfter("本地:").substringBefore("\n").trim()
            }
        }
        
        // 验证文件是否存在
        if (filePath != null) {
            val backupFile = File(filePath)
            assertTrue(backupFile.exists(), "备份文件应该存在")
            assertTrue(backupFile.length() > 0, "备份文件不应为空")
            
            // 清理测试生成的文件
            backupFile.delete()
        }
        
        Timber.i("[Mobile] INFO [Test] executeBackup测试通过; Context: 成功执行完整备份流程")
    }

    @After
    fun tearDown() {
        Timber.i("[Mobile] INFO [Test] 完成BackupManager设备测试; Context: Test duration=${System.currentTimeMillis() - testStartTime}ms")
        Timber.uprootAll() // 移除日志记录器
    }
} 