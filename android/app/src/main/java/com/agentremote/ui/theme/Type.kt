package com.agentremote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.agentremote.R

// Bundled developer-grade JetBrains Mono font family (completely overrides Android system fonts)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

val TerminalFontFamily = JetBrainsMono

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextPrimary
    ),
    displaySmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.15.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextMuted
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.6.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
        fontFeatureSettings = "'tnum' 1, 'zero' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        color = TextMuted
    )
)
