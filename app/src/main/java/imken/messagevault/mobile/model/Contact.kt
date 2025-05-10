package imken.messagevault.mobile.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/**
 * 联系人数据模型
 *
 * 表示一个联系人，包含ID、姓名、电话号码列表和电子邮件列表
 */
data class Contact(
    /**
     * 联系人ID
     */
    @Expose
    @SerializedName("id")
    val id: Long,
    
    /**
     * 联系人姓名
     */
    @Expose
    @SerializedName("name")
    val name: String,
    
    /**
     * 联系人电话号码列表
     */
    @Expose
    @SerializedName("phones")
    val phoneNumbers: MutableList<String>,

    /**
     * 联系人电子邮件列表
     */
    @Expose
    @SerializedName("emails")
    val emails: List<String>? = null,
    
    /**
     * 联系人地址信息
     */
    @Expose
    @SerializedName("addresses")
    val addresses: List<Address>? = null,
    
    /**
     * 联系人头像数据（Base64编码）
     * 不使用@Expose注解，这样不会序列化到JSON中，减小文件大小
     */
    val photoData: String? = null,
    
    /**
     * 联系人备注
     */
    @Expose
    @SerializedName("note")
    val note: String? = null,
    
    /**
     * 联系人所属组
     */
    @Expose
    @SerializedName("groups")
    val groups: List<String>? = null,
    
    /**
     * 联系人网站
     */
    @Expose
    @SerializedName("websites")
    val websites: List<String>? = null,
    
    /**
     * 联系人事件（如生日、纪念日）
     */
    @Expose
    @SerializedName("events")
    val events: List<Event>? = null,
    
    /**
     * 联系人关系（如配偶、子女）
     */
    @Expose
    @SerializedName("relationships")
    val relationships: List<Relationship>? = null,
    
    /**
     * 联系人社交资料
     */
    @Expose
    @SerializedName("social_profiles")
    val socialProfiles: List<SocialProfile>? = null
) {
    /**
     * 联系人地址数据类
     */
    data class Address(
        /**
         * 地址类型（家庭、工作等）
         */
        @Expose
        @SerializedName("type")
        val type: String,
        
        /**
         * 地址内容
         */
        @Expose
        @SerializedName("value")
        val value: String
    )
    
    /**
     * 联系人事件数据类
     */
    data class Event(
        /**
         * 事件类型（生日、纪念日等）
         */
        @Expose
        @SerializedName("type")
        val type: String,
        
        /**
         * 事件日期
         */
        @Expose
        @SerializedName("date")
        val date: String
    )
    
    /**
     * 联系人关系数据类
     */
    data class Relationship(
        /**
         * 关系类型（配偶、子女等）
         */
        @Expose
        @SerializedName("type")
        val type: String,
        
        /**
         * 关系人名称
         */
        @Expose
        @SerializedName("name")
        val name: String
    )
    
    /**
     * 联系人社交资料数据类
     */
    data class SocialProfile(
        /**
         * 社交媒体类型（如Facebook、Twitter等）
         */
        @Expose
        @SerializedName("type")
        val type: String,
        
        /**
         * 社交媒体账号或链接
         */
        @Expose
        @SerializedName("value")
        val value: String
    )
} 