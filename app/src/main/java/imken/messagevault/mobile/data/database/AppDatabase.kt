package imken.messagevault.mobile.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import imken.messagevault.mobile.data.dao.CallLogDao
import imken.messagevault.mobile.data.dao.ContactDao
import imken.messagevault.mobile.data.dao.MessageDao
import imken.messagevault.mobile.data.entity.CallLogsEntity
import imken.messagevault.mobile.data.entity.ContactsEntity
import imken.messagevault.mobile.data.entity.MessageEntity

/**
 * 应用数据库类
 */
@Database(
    entities = [
        CallLogsEntity::class,
        MessageEntity::class,
        ContactsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    /**
     * 获取通话记录DAO
     */
    abstract fun callLogDao(): CallLogDao
    
    /**
     * 获取短信消息DAO
     */
    abstract fun messageDao(): MessageDao
    
    /**
     * 获取联系人DAO
     */
    abstract fun contactDao(): ContactDao
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * 获取数据库实例
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "message_vault.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 