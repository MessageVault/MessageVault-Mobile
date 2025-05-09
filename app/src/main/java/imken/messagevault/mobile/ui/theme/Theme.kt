package imken.messagevault.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 信驿云储应用主题
 * 
 * 实现Material Design 3（Material You）设计规范
 * 提供以下功能：
 * 1. 动态颜色支持（Android 12+ / API 31+）
 * 2. 针对低版本API的静态颜色方案回退
 * 3. 深色/浅色主题切换
 * 
 * 参考资料：
 * - Material Design 3颜色系统: https://m3.material.io/styles/color/overview
 * - 动态颜色概览: https://m3.material.io/styles/color/dynamic-color/overview
 */

// 深色模式下的主题颜色
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),        // 蓝色，信驿主色
    onPrimary = Color(0xFF000000),      // 黑色，在主色上的文本颜色
    primaryContainer = Color(0xFF0D47A1),// 深蓝色，主色容器
    onPrimaryContainer = Color(0xFFE3F2FD),// 浅蓝色，主色容器上的文本
    
    secondary = Color(0xFF80DEEA),      // 青色，辅助色
    onSecondary = Color(0xFF000000),    // 黑色，在辅助色上的文本颜色
    secondaryContainer = Color(0xFF006064),// 深青色，辅助色容器
    onSecondaryContainer = Color(0xFFE0F7FA),// 浅青色，辅助色容器上的文本
    
    tertiary = Color(0xFF80CBC4),       // 第三色
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF004D40),
    onTertiaryContainer = Color(0xFFE0F2F1),
    
    background = Color(0xFF121212),     // 深灰色，背景色
    onBackground = Color(0xFFFFFFFF),   // 白色，背景上的文本
    surface = Color(0xFF1E1E1E),        // 暗表面色
    onSurface = Color(0xFFFFFFFF),      // 白色，表面上的文本
    surfaceVariant = Color(0xFF2C2C2C), // 变体表面色
    onSurfaceVariant = Color(0xFFDDDDDD),// 变体表面色上的文本
    
    error = Color(0xFFEF5350),          // 红色，错误色
    onError = Color(0xFF000000),        // 黑色，错误色上的文本
    errorContainer = Color(0xFFC62828), // 错误容器
    onErrorContainer = Color(0xFFFFCDD2)// 错误容器上的文本
)

// 浅色模式下的主题颜色
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),        // 蓝色，信驿主色
    onPrimary = Color(0xFFFFFFFF),      // 白色，在主色上的文本颜色
    primaryContainer = Color(0xFFBBDEFB),// 浅蓝色，主色容器
    onPrimaryContainer = Color(0xFF0D47A1),// 深蓝色，主色容器上的文本
    
    secondary = Color(0xFF00ACC1),      // 青色，辅助色
    onSecondary = Color(0xFFFFFFFF),    // 白色，在辅助色上的文本颜色
    secondaryContainer = Color(0xFFB2EBF2),// 浅青色，辅助色容器
    onSecondaryContainer = Color(0xFF006064),// 深青色，辅助色容器上的文本
    
    tertiary = Color(0xFF009688),       // 第三色
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB2DFDB),
    onTertiaryContainer = Color(0xFF004D40),
    
    background = Color(0xFFFAFAFA),     // 浅灰色，背景色
    onBackground = Color(0xFF000000),   // 黑色，背景上的文本
    surface = Color(0xFFFFFFFF),        // 白色表面色
    onSurface = Color(0xFF000000),      // 黑色，表面上的文本
    surfaceVariant = Color(0xFFF5F5F5), // 变体表面色
    onSurfaceVariant = Color(0xFF616161),// 变体表面色上的文本
    
    error = Color(0xFFB00020),          // 红色，错误色
    onError = Color(0xFFFFFFFF),        // 白色，错误色上的文本
    errorContainer = Color(0xFFF8D7DA), // 错误容器
    onErrorContainer = Color(0xFF721C24)// 错误容器上的文本
)

/**
 * 信驿云储应用主题
 * 
 * @param darkTheme 是否使用深色主题
 * @param dynamicColor 是否使用动态颜色（仅Android 12+支持）
 * @param content 包含在主题中的内容
 */
@Composable
fun MessageVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 默认启用动态颜色，仅在Android 12+上生效
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // 根据平台版本和用户设置决定使用的颜色方案
    val colorScheme = when {
        // 如果支持动态颜色（Android 12+）且用户选择使用动态颜色
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 根据深色/浅色模式选择预设颜色方案
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    // 设置状态栏和导航栏颜色以匹配主题
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // 应用Material3主题
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * 应用排版样式
 * 
 * 基于Material Design 3排版规范定义的文字样式
 * 参考：https://m3.material.io/styles/typography/overview
 */
private val Typography = Typography(
    // 默认使用Material 3的Typography设置
) 