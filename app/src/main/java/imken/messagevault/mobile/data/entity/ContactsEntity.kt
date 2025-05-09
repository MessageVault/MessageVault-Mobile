package imken.messagevault.mobile.data.entity

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * 联系人实体类
 * 
 * 用于在Room数据库中存储联系人数据
 */
@Entity(tableName = "contacts")
data class ContactsEntity(
    /**
     * 联系人ID，主键
     */
    @PrimaryKey
    @SerializedName("id")
    val id: Long,
    
    /**
     * 联系人姓名
     */
    @SerializedName("name")
    val name: String,
    
    /**
     * 联系人电话号码列表
     */
    @SerializedName("phone_numbers")
    val phoneNumbers: List<String>,
    
    /**
     * 联系人电子邮件列表
     */
    @SerializedName("emails")
    val emails: List<String>,
    
    /**
     * 联系人头像URI
     */
    @SerializedName("photo_uri")
    val photoUri: String? = null,
    
    /**
     * 最后更新时间
     */
    @SerializedName("last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.createStringArrayList() ?: emptyList(),
        parcel.createStringArrayList() ?: emptyList()
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(name)
        parcel.writeStringList(phoneNumbers)
        parcel.writeStringList(emails)
    }
    
    override fun describeContents(): Int {
        return 0
    }
    
    companion object CREATOR : Parcelable.Creator<ContactsEntity> {
        override fun createFromParcel(parcel: Parcel): ContactsEntity {
            return ContactsEntity(parcel)
        }
        
        override fun newArray(size: Int): Array<ContactsEntity?> {
            return arrayOfNulls(size)
        }
    }
}
