package imken.messagevault.mobile.models

data class ContactData(
    val id: Long,
    val name: String,
    val phoneNumbers: List<String>,
    val emails: List<String>
)
