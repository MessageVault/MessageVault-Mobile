package imken.messagevault.mobile.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import timber.log.Timber

// DataStore 实例扩展属性
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 应用配置
 * 
 * 管理应用的各种配置信息
 */
class Config(private val context: Context) {
    private val dataStore = context.dataStore
    private val mContext: Context = context.applicationContext

    companion object {
        private var sInstance: Config? = null

        fun getInstance(context: Context): Config {
            if (sInstance == null) {
                synchronized(Config::class.java) {
                    if (sInstance == null) {
                        sInstance = Config(context)
                    }
                }
            }
            return sInstance!!
        }

        // 默认配置常量
        private const val DEFAULT_SERVER_URL = "https://api.messagevault.example.com"
        private const val DEFAULT_API_VERSION = "v1"
        private const val DEFAULT_CONNECTION_TIMEOUT = 30000
        private const val DEFAULT_BACKUP_INTERVAL = 24 // 小时
        private const val DEFAULT_BACKUP_DIR = "backups"
        private const val DEFAULT_LANGUAGE = "zh"

        // 配置键
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_API_VERSION = stringPreferencesKey("api_version") 
        private val KEY_CONNECTION_TIMEOUT = intPreferencesKey("connection_timeout")
        private val KEY_BACKUP_INTERVAL = intPreferencesKey("backup_interval")
        private val KEY_BACKUP_DIR = stringPreferencesKey("backup_directory")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
    }

    // 备份目录名称
    private val backupDirectoryName = "backups"
    
    /**
     * 初始化配置
     */
    init {
        Timber.d("[Mobile] DEBUG [Config] 配置初始化")
    }
    
    /**
     * 获取备份目录名称
     * 
     * @return 备份目录名称
     */
    fun getBackupDirectoryName(): String {
        return backupDirectoryName
    }
    
    /**
     * 获取应用版本
     * 
     * @return 应用版本名称
     */
    fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            Timber.e(e, "[Mobile] ERROR [Config] 获取应用版本失败")
            "未知版本"
        }
    }

    /**
     * 获取服务器URL
     */
    fun getServerUrl(): String {
        val serverUrlFlow: Flow<String> = dataStore.data.map { preferences ->
            preferences[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
        }
        return runBlocking { serverUrlFlow.first() }
    }

    /**
     * 设置服务器URL
     */
    fun setServerUrl(url: String) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_SERVER_URL] = url
            }
        }
    }

    /**
     * 获取API版本
     */
    fun getApiVersion(): String {
        val apiVersionFlow: Flow<String> = dataStore.data.map { preferences ->
            preferences[KEY_API_VERSION] ?: DEFAULT_API_VERSION
        }
        return runBlocking { apiVersionFlow.first() }
    }

    /**
     * 设置API版本
     */
    fun setApiVersion(version: String) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_API_VERSION] = version
            }
        }
    }

    /**
     * 获取完整API URL
     */
    fun getFullApiUrl(): String {
        return "${getServerUrl()}/${getApiVersion()}"
    }

    /**
     * 获取认证令牌
     */
    fun getAuthToken(): String? {
        val authTokenFlow: Flow<String?> = dataStore.data.map { preferences ->
            preferences[KEY_AUTH_TOKEN]
        }
        return runBlocking { authTokenFlow.first() }
    }

    /**
     * 设置认证令牌
     */
    fun setAuthToken(token: String?) {
        if (token == null) {
            runBlocking {
                dataStore.edit { preferences ->
                    preferences.remove(KEY_AUTH_TOKEN)
                }
            }
        } else {
            runBlocking {
                dataStore.edit { preferences ->
                    preferences[KEY_AUTH_TOKEN] = token
                }
            }
        }
    }

    /**
     * 获取首选语言
     */
    fun getLanguage(): String {
        val languageFlow: Flow<String> = dataStore.data.map { preferences ->
            preferences[KEY_LANGUAGE] ?: DEFAULT_LANGUAGE
        }
        return runBlocking { languageFlow.first() }
    }

    /**
     * 设置首选语言
     */
    fun setLanguage(langCode: String) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_LANGUAGE] = langCode
            }
            Timber.i("[Mobile] INFO [Config] 语言设置已更新为 $langCode; Context: 用户操作")
        }
    }
    
    /**
     * 获取连接超时时间（毫秒）
     */
    fun getConnectionTimeout(): Int {
        val connectionTimeoutFlow: Flow<Int> = dataStore.data.map { preferences ->
            preferences[KEY_CONNECTION_TIMEOUT] ?: DEFAULT_CONNECTION_TIMEOUT
        }
        return runBlocking { connectionTimeoutFlow.first() }
    }
    
    /**
     * 设置连接超时时间（毫秒）
     */
    fun setConnectionTimeout(timeout: Int) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_CONNECTION_TIMEOUT] = timeout
            }
        }
    }
    
    /**
     * 获取备份间隔（小时）
     */
    fun getBackupInterval(): Int {
        val backupIntervalFlow: Flow<Int> = dataStore.data.map { preferences ->
            preferences[KEY_BACKUP_INTERVAL] ?: DEFAULT_BACKUP_INTERVAL
        }
        return runBlocking { backupIntervalFlow.first() }
    }
    
    /**
     * 设置备份间隔（小时）
     */
    fun setBackupInterval(hours: Int) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences[KEY_BACKUP_INTERVAL] = hours
            }
        }
    }
    
    /**
     * 应用语言设置到指定上下文
     */
    fun applyLanguage(context: Context) {
        val langCode = getLanguage()
        val locale = java.util.Locale(langCode)
        java.util.Locale.setDefault(locale)
        
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        context.createConfigurationContext(configuration)
    }
}