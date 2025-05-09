package imken.messagevault.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import imken.messagevault.mobile.R

/**
 * 预览屏幕
 * 
 * 实现数据预览功能界面，显示短信和通话记录的预览
 * 
 * @param permissionsGranted 是否已获取必要权限
 */
@Composable
fun PreviewScreen(
    permissionsGranted: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (permissionsGranted) {
            PreviewContent()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.permission_required),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = stringResource(id = R.string.permission_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}

/**
 * 预览内容区域
 */
@Composable
fun PreviewContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.preview_tab),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        // 选项卡
        val selectedTab = remember { mutableStateOf(0) }
        val tabs = listOf("短信", "通话记录")
        
        TabRow(
            selectedTabIndex = selectedTab.value,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab.value == index,
                    onClick = { selectedTab.value = index },
                    text = { Text(title) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 预览内容
        when (selectedTab.value) {
            0 -> SmsPreview()
            1 -> CallLogPreview()
        }
    }
}

/**
 * 短信预览
 */
@Composable
fun SmsPreview() {
    // 示例数据
    val smsItems = listOf(
        PreviewItem(
            id = 1,
            title = "+1 555-123-4567",
            subtitle = "2025-04-18 10:30",
            content = "嗨，你好！我们下午3点在咖啡厅见面吧。"
        ),
        PreviewItem(
            id = 2,
            title = "+1 555-987-6543",
            subtitle = "2025-04-18 09:15",
            content = "请记得带上项目文档，谢谢！"
        ),
        PreviewItem(
            id = 3,
            title = "+1 555-111-2222",
            subtitle = "2025-04-17 18:45",
            content = "您的包裹已送达，请查收。"
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (smsItems.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Preview,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "暂无短信数据",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(smsItems) { item ->
                    PreviewItemCard(item)
                }
            }
        }
    }
}

/**
 * 通话记录预览
 */
@Composable
fun CallLogPreview() {
    // 示例数据
    val callItems = listOf(
        PreviewItem(
            id = 1,
            title = "+1 555-123-4567",
            subtitle = "2025-04-18 11:20 (呼出 · 2分30秒)",
            content = ""
        ),
        PreviewItem(
            id = 2,
            title = "+1 555-987-6543",
            subtitle = "2025-04-18 10:05 (未接)",
            content = ""
        ),
        PreviewItem(
            id = 3,
            title = "+1 555-111-2222",
            subtitle = "2025-04-17 19:30 (呼入 · 5分45秒)",
            content = ""
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (callItems.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PermDeviceInformation,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "暂无通话记录",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(callItems) { item ->
                    PreviewItemCard(item)
                }
            }
        }
    }
}

/**
 * 预览项数据类
 */
data class PreviewItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val content: String
)

/**
 * 预览项卡片
 */
@Composable
fun PreviewItemCard(item: PreviewItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            
            if (item.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
} 