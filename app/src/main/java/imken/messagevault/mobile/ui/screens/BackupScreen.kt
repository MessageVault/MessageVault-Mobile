package imken.messagevault.mobile.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import imken.messagevault.mobile.R
import imken.messagevault.mobile.ui.theme.MessageVaultTheme

/**
 * 备份屏幕
 *
 * 显示备份功能界面，包括备份按钮和状态信息
 *
 * @param permissionsGranted 是否已授权所需权限
 * @param isOperating 是否有操作正在进行
 * @param backupStatus 备份状态信息
 * @param onBackupClick 备份按钮点击回调
 */
@Composable
fun BackupScreen(
    permissionsGranted: Boolean,
    isOperating: Boolean,
    backupStatus: String?,
    onBackupClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            text = stringResource(id = R.string.backup_tab),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        if (!permissionsGranted) {
            // 权限提示
            PermissionRequiredCard()
        } else {
            // 备份信息卡片
            BackupInfoCard(isOperating, backupStatus, onBackupClick)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 备份帮助卡片
            BackupHelpCard()
        }
    }
}

/**
 * 权限提示卡片
 *
 * 当应用缺少所需权限时显示
 */
@Composable
fun PermissionRequiredCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(48.dp)
                    .padding(bottom = 8.dp)
            )
            
            Text(
                text = stringResource(id = R.string.permission_required),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(id = R.string.permission_prompt),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 设置按钮
            Button(
                onClick = { /* TODO: 打开应用设置 */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(text = stringResource(id = R.string.settings))
            }
        }
    }
}

/**
 * 备份信息卡片
 *
 * 显示备份状态和操作按钮
 */
@Composable
fun BackupInfoCard(
    isOperating: Boolean,
    backupStatus: String?,
    onBackupClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = "数据备份",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 状态信息
            Text(
                text = backupStatus ?: stringResource(id = R.string.status_initial),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 备份按钮
            Button(
                onClick = onBackupClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isOperating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                )
            ) {
                if (isOperating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Backup,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.backup_button),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * 备份帮助卡片
 *
 * 显示备份相关的帮助信息
 */
@Composable
fun BackupHelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "关于备份",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val permissions = remember {
                listOf(
                    "读取短信 (${Manifest.permission.READ_SMS})" to "用于备份短信内容",
                    "读取通话记录 (${Manifest.permission.READ_CALL_LOG})" to "用于备份通话历史",
                    "存储空间 (${Manifest.permission.WRITE_EXTERNAL_STORAGE})" to "用于保存备份文件"
                )
            }
            
            LazyColumn {
                items(permissions) { (permission, description) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp)
                        )
                        
                        Column {
                            Text(
                                text = permission,
                                style = MaterialTheme.typography.titleSmall
                            )
                            
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                item {
                    Divider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
                
                item {
                    Text(
                        text = "备份存储位置",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    Text(
                        text = "备份文件将保存在应用私有存储空间，卸载应用会同时删除备份。请定期将备份导出到其他位置。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * 预览函数
 */
@Preview(showBackground = true)
@Composable
fun BackupScreenPreview() {
    MessageVaultTheme {
        BackupScreen(
            permissionsGranted = true,
            isOperating = false,
            backupStatus = "上次备份：2023-01-01 12:00",
            onBackupClick = {}
        )
    }
} 