package imken.messagevault.mobile.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import imken.messagevault.mobile.data.entity.ContactsEntity

/**
 * 联系人DAO接口
 */
@Dao
interface ContactDao {
    
    /**
     * 插入联系人
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactsEntity>)
    
    /**
     * 获取所有联系人，按姓名排序
     */
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getAllContacts(): List<ContactsEntity>
    
    /**
     * 删除所有联系人
     */
    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()
    
    /**
     * 获取联系人数量
     */
    @Query("SELECT COUNT(id) FROM contacts")
    suspend fun getContactsCount(): Int
} 