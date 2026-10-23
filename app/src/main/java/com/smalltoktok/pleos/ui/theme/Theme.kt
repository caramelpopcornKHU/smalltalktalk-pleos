package com.smalltoktok.pleos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.smalltoktok.pleos.R

/** 폰 앱과 같은 Wise 계열 팔레트 (smalltalktalk apps/mobile/src/theme/tokens.ts) */
object Palette {
    val bg = Color(0xFFE8EBE6)
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF0E0F0C)
    val muted = Color(0xFF5D615A)
    val accent = Color(0xFF9FE870)
    val onAccent = Color(0xFF163300)
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

// 차량 화면은 팔 길이만큼 떨어져 보므로 폰보다 글자를 크게 둔다
private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 52.sp),
    headlineMedium = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 42.sp),
    titleLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    bodyLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 32.sp),
    labelLarge = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
)

@Composable
fun SmallTokTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Palette.bg,
            surface = Palette.surface,
            onBackground = Palette.ink,
            onSurface = Palette.ink,
            primary = Palette.ink,
            onPrimary = Palette.surface,
            secondary = Palette.accent,
            onSecondary = Palette.onAccent,
        ),
        typography = typography,
        content = content,
    )
}
