package imken.messagevault.mobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import imken.messagevault.mobile.R
import imken.messagevault.mobile.models.BackupFile
import imken.messagevault.mobile.ui.viewmodels.RestoreViewModel
import imken.messagevault.mobile.MainActivity
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * 恢复屏幕
 *
 * 显示备份文件列表并提供恢复功能
 * 遵循Material Design 3规范设计
 *
 * @param backupFiles 备份文件列表
 * @param isOperating 是否有操作正在进行
 * @param restoreStatus 恢复状态信息
 * @param onRestoreClick 恢复按钮点击回调
 * @param onBackupItemClick 备份项点击回调
 * @param selectedBackupFile 当前选中的备份文件
 * @param viewModel 恢复视图模型
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(
    backupFiles: List<BackupFile>,
    isOperating: Boolean,
    restoreStatus: String?,
    onRestoreClick: (BackupFile) -> Unit,
    onBackupItemClick: (BackupFile) -> Unit,
    selectedBackupFile: BackupFile? = null,
    viewModel: RestoreViewModel
) {
    val context = LocalContext.current
    
    // 显示需要默认短信应用对话框
    if (viewModel.needDefaultSmsApp.value) {
        AlertDialog(
            onDismissRequest = { viewModel.resetNeedDefaultSmsApp() },
            title = { Text(stringResource(R.string.permission_required)) },
            text = { 
                Text(
                    "恢复短信需要临时将此应用设置为默认短信应用。\n\n" +
                    "恢复完成后，您可以将其改回原来的应用。\n\n" +
                    "在接下来的系统界面中选择\"是\"，将信驿云储设为默认短信应用，以开始恢复任务。"
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 先检查一次是否已经是默认应用，可能系统已授权但UI状态未更新
                        val isDefault = viewModel.checkAndUpdateDefaultSmsAppStatus()
                        
                        if (isDefault) {
                            // 如果已经是默认应用，直接关闭对话框并开始恢复
                            Timber.i("[Mobile] INFO [Restore] 应用已是默认短信应用，无需再请求权限")
                            viewModel.resetNeedDefaultSmsApp()
                            selectedBackupFile?.let { onRestoreClick(it) }
                        } else {
                            // 否则请求成为默认应用
                            viewModel.resetNeedDefaultSmsApp()
                            if (context is MainActivity) {
                                context.requestDefaultSmsApp()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.resetNeedDefaultSmsApp() }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            text = stringResource(id = R.string.restore_tab),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 状态信息
        if (restoreStatus != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (restoreStatus.contains("失败"))
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (restoreStatus.contains("失败"))
                            Icons.Default.Error
                        else
                            Icons.Default.Info,
                        contentDescription = null,
                        tint = if (restoreStatus.contains("失败"))
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    
                    Text(
                        text = restoreStatus.toString(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        
        // 获取恢复状态和进度
        val restoreState by viewModel.restoreState.collectAsState()
        val restoreProgress by viewModel.restoreProgress.collectAsState()
        val restorePhase = viewModel.restorePhase.value
        
        // 恢复进度指示器
        if (isOperating) {
            // 显示包含阶段信息的进度指示器
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 循环进度条
                if (restoreProgress <= 0) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                } else {
                    // 确定性进度条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                    ) {
                        // 进度条
                        LinearProgressIndicator(
                            progress = restoreProgress / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    
                    // 进度文本
                    Text(
                        text = "$restoreProgress%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                // 阶段描述
                if (restorePhase != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = restorePhase,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        // 备份文件列表
        if (backupFiles.isEmpty()) {
            EmptyBackupList()
        } else {
            Text(
                text = "可用备份文件",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(backupFiles) { backupFile ->
                    BackupFileItem(
                        backupFile = backupFile,
                        isSelected = selectedBackupFile == backupFile,
                        onClick = { onBackupItemClick(backupFile) }
                    )
                }
            }
            
            // 恢复按钮
            Button(
                onClick = { selectedBackupFile?.let { onRestoreClick(it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(vertical = 8.dp),
                enabled = selectedBackupFile != null && !isOperating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "恢复选中的备份",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * 备份文件项
 *
 * 显示单个备份文件信息
 *
 * @param backupFile 备份文件对象
 * @param isSelected 是否被选中
 * @param onClick 点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupFileItem(
    backupFile: BackupFile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(backupFile) { 
        dateFormat.format(backupFile.creationDate) 
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = if (isSelected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = backupFile.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.sms_count, backupFile.smsCount),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                Badge(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.call_log_count, backupFile.callLogsCount),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                Badge(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(
                        text = formatFileSize(backupFile.fileSize),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 空备份列表视图
 */
@Composable
fun EmptyBackupList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp)
        )
        
        Text(
            text = stringResource(id = R.string.no_backups_found),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "请先创建备份，再使用恢复功能",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(size: Long): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return String.format("%.2f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.2f MB", mb)
}
