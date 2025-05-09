package imken.messagevault.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import imken.messagevault.mobile.BuildConfig
import imken.messagevault.mobile.R

/**
 * 更多选项屏幕
 * 
 * 显示应用设置、关于信息等更多选项
 * 
 * @param onNavigateToRestore 导航到恢复屏幕的回调
 */
@Composable
fun MoreScreen(
    onNavigateToRestore: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            text = stringResource(id = R.string.more_tab),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // 功能卡片
        FeaturesCard(onNavigateToRestore)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 设置卡片
        SettingsCard()
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 关于信息卡片
        AboutCard()
    }
}

/**
 * 功能卡片
 * 
 * 显示应用的主要功能快捷方式
 * 
 * @param onNavigateToRestore 导航到恢复屏幕的回调
 */
@Composable
fun FeaturesCard(onNavigateToRestore: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "快速操作",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 恢复数据按钮
            FilledTonalButton(
                onClick = onNavigateToRestore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(text = stringResource(id = R.string.restore_button))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 导出数据按钮
            OutlinedButton(
                onClick = { /* TODO 导出功能 */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(text = "导出数据")
            }
        }
    }
}

/**
 * 设置选项卡片
 */
@Composable
fun SettingsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "应用设置",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 语言设置
            SettingItem(
                icon = { Icon(Icons.Default.Language, contentDescription = null) },
                title = "语言设置",
                subtitle = "选择应用显示语言",
                onClick = { /* TODO */ }
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // 存储设置
            SettingItem(
                icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                title = "存储设置",
                subtitle = "管理备份文件和存储位置",
                onClick = { /* TODO */ }
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // 网络设置
            SettingItem(
                icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                title = "网络设置",
                subtitle = "配置服务器地址和连接选项",
                onClick = { /* TODO */ }
            )
        }
    }
}

/**
 * 关于卡片
 */
@Composable
fun AboutCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "关于",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            AboutItem(
                title = "应用名称",
                value = stringResource(id = R.string.app_name)
            )
            
            AboutItem(
                title = "版本",
                value = BuildConfig.VERSION_NAME
            )
            
            AboutItem(
                title = "开发者",
                value = "MessageVault Team"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "信驿云储是一款开源的短信和通话记录备份工具，支持本地备份和云端同步。保护您的重要通信数据，随时随地访问。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 设置项
 */
@Composable
fun SettingItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "打开"
            )
        }
    }
}

/**
 * 关于项
 */
@Composable
fun AboutItem(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(100.dp)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
} 