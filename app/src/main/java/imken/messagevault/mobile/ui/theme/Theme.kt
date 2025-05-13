package imken.messagevault.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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

// 使用预定义的颜色创建深色主题颜色方案
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkOnPrimary,
    onSecondary = DarkOnSecondary,
    onTertiary = DarkOnTertiary,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
    error = Error,
)

// 使用预定义的颜色创建浅色主题颜色方案
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = LightTertiary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = LightOnPrimary,
    onSecondary = LightOnSecondary,
    onTertiary = LightOnTertiary,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    error = Error,
)

/**
 * 应用主题组件
 * 
 * @param darkTheme 是否使用深色主题，默认根据系统设置自动选择
 * @param dynamicColor 是否使用动态颜色（仅Android 12及以上支持），默认开启
 * @param content 需要应用主题的内容
 */
@Composable
fun MessageVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // 根据系统版本、动态颜色支持和暗色主题选择确定颜色方案
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    // 设置状态栏颜色和样式
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // 应用Material主题
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}