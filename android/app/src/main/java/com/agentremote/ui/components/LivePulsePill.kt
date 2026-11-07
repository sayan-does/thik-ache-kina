package com.agentremote.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    // 2.5s calm breathing pulse for live operations
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    val (bgTint, borderTint, ledColor, labelText, isPulsing) = when (state) {
        AgentState.WAITING_FOR_INPUT -> StatusConfig(
            AccentAmberMuted.copy(alpha = 0.4f),
            AccentAmber.copy(alpha = 0.8f),
            AccentAmber,
            "ACTION REQUIRED",
            true
        )
        AgentState.RUNNING -> StatusConfig(
            AccentEmeraldMuted.copy(alpha = 0.35f),
            AccentEmerald.copy(alpha = 0.7f),
            AccentEmerald,
            "ACTIVE",
            true
        )
        AgentState.CONNECTING -> StatusConfig(
            SurfaceElevated,
            AccentCyan.copy(alpha = 0.6f),
            AccentCyan,
            "CONNECTING",
            true
        )
        AgentState.DISCONNECTED -> StatusConfig(
            SurfaceElevated,
            BorderDark,
            TextMuted,
            "OFFLINE",
            false
        )
        AgentState.COMPLETED -> StatusConfig(
            AccentEmeraldMuted.copy(alpha = 0.25f),
            AccentEmerald.copy(alpha = 0.5f),
            AccentEmerald,
            "COMPLETED",
            false
        )
        AgentState.ERROR -> StatusConfig(
            AccentCrimsonMuted.copy(alpha = 0.4f),
            AccentCrimson.copy(alpha = 0.8f),
            AccentCrimson,
            "ERROR",
            false
        )
        AgentState.IDLE -> StatusConfig(
            SurfaceElevated,
            BorderDark,
            TextMuted,
            "STANDBY",
            false
        )
    }

    Row(
        modifier = modifier
            .background(bgTint, RoundedCornerShape(4.dp))
            .border(1.dp, borderTint, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Hardware LED dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(if (isPulsing) dotAlpha else 1f)
                .background(ledColor, CircleShape)
        )

        // Monospace status label (No emojis)
        Text(
            text = labelText,
            color = if (state == AgentState.DISCONNECTED) TextSecondary else ledColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = TerminalFontFamily,
            letterSpacing = 0.8.sp
        )
    }
}

private data class StatusConfig(
    val bgTint: Color,
    val borderTint: Color,
    val ledColor: Color,
    val labelText: String,
    val isPulsing: Boolean
)
