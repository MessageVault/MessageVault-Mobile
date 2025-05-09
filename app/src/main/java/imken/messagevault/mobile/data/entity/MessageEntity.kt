package imken.messagevault.mobile.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Int? = 0,
    val status: Int? = 0,
    val threadId: Long = 0
) 