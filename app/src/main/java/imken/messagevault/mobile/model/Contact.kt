package imken.messagevault.mobile.model

import com.google.gson.annotations.SerializedName

/**
 * 联系人数据模型
 *
 * 表示一个联系人，包含ID、姓名和电话号码列表
 */
data class Contact(
    /**
     * 联系人ID
     */
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
    val phoneNumbers: MutableList<String>,

    /**
     * 联系人电子邮件列表
     */
    @SerializedName("emails")
    val emails: List<String>? = null
) 