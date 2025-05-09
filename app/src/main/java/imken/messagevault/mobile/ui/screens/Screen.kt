package imken.messagevault.mobile.ui.screens

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Backup : Screen("backup")
    object Restore : Screen("restore")
    object Settings : Screen("settings")
    
    object MessageDetail : Screen("message_detail/{messageId}") {
        fun createRoute(messageId: String): String {
            return "message_detail/$messageId"
        }
    }
    
    object CallDetail : Screen("call_detail/{callId}") {
        fun createRoute(callId: String): String {
            return "call_detail/$callId"
        }
    }
}
