package com.agentremote.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentremote.data.AgentState
import com.agentremote.ui.theme.*

@Composable
fun LivePulsePill(state: AgentState, modifier: Modifier = Modifier) {
    // 2.5s gentle pulse animation for thinking/running
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val (bgColor, borderColor, textColor, text, isPulsing) = when (state) {
        AgentState.WAITING_FOR_INPUT -> Tuple5(
            AccentAmberMuted.copy(alpha = 0.5f),
            AccentAmber,
            AccentAmber,
            "ACTION REQUIRED ⚠️",
            true
        )
        AgentState.RUNNING -> Tuple5(
            SurfaceElevated,
            AccentCyan.copy(alpha = 0.5f),
            AccentCyan,
            "AGENT ACTIVE ⚙️",
            true
        )
        AgentState.CONNECTING -> Tuple5(
            SurfaceElevated,
            BorderDark,
            TextSecondary,
            "RECONNECTING 🔄",
            true
        )
        AgentState.DISCONNECTED -> Tuple5(
            AccentCrimsonMuted.copy(alpha = 0.4f),
            AccentCrimson.copy(alpha = 0.5f),
            AccentCrimson,
            "OFFLINE 💤",
            false
        )
        AgentState.COMPLETED -> Tuple5(
            AccentEmeraldMuted.copy(alpha = 0.4f),
            AccentEmerald.copy(alpha = 0.6f),
            AccentEmerald,
            "TASK COMPLETE ✓",
            false
        )
        AgentState.ERROR -> Tuple5(
            AccentCrimsonMuted,
            AccentCrimson,
            AccentCrimson,
            "PROCESS ERROR ✕",
            false
        )
        AgentState.IDLE -> Tuple5(
            SurfaceElevated,
            BorderDark,
            TextMuted,
            "STANDBY ●",
            false
        )
    }

    Box(
        modifier = modifier
            .alpha(if (isPulsing) alphaAnim else 1f)
            .background(bgColor, RoundedCornerShape(100.dp))
            .border(1.dp, borderColor, RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = TerminalFontFamily,
            letterSpacing = 0.5.sp
        )
    }
}

private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
