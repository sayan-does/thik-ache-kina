package com.agentremote.ui.components

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentremote.ui.theme.*

@Composable
fun SplitThumbBar(
    onRawKey: (String) -> Unit,
    onSendText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    var isTextDialogOpen by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var ctrlCMessage by remember { mutableStateOf<String?>(null) }

    fun triggerHaptic(durationMs: Long = 40) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .border(1.dp, BorderDark, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(12.dp)
    ) {
        // Feedback Pill if Ctrl+C pressed
        AnimatedVisibility(visible = ctrlCMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(AccentCrimsonMuted, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ctrlCMessage ?: "",
                    color = AccentCrimson,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFontFamily
                )
            }
        }

        // Row 1: Split Thumb Zone (Left Thumb: ▲ ▼ | Right Thumb: ␣ ⏎)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Thumb Navigation (▲, ▼)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyButton(label = "▲", onClick = {
                    triggerHaptic(20)
                    onRawKey("UP")
                })
                KeyButton(label = "▼", onClick = {
                    triggerHaptic(20)
                    onRawKey("DOWN")
                })
            }

            // Right Thumb Confirmation (Space, Enter)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyButton(label = "␣ Space", minWidth = 80.dp, onClick = {
                    triggerHaptic(20)
                    onRawKey("SPACE")
                })
                KeyButton(
                    label = "⏎ Enter",
                    minWidth = 80.dp,
                    isPrimary = true,
                    onClick = {
                        triggerHaptic(30)
                        onRawKey("ENTER")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2: Custom Text Prompt Bar & Protected Ctrl+C Latch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collapsed Text Input Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                    .clickable { isTextDialogOpen = true }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "⌨️ Type custom response...",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            // Protected Ctrl+C Latch (Requires 400ms Hold or notifies user)
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .width(96.dp)
                    .background(AccentCrimsonMuted.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .border(1.dp, AccentCrimson.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                ctrlCMessage = "⚠️ Hold 400ms to send SIGINT (^C)"
                            },
                            onLongPress = {
                                triggerHaptic(80)
                                onRawKey("SIGINT")
                                ctrlCMessage = "🛑 SIGINT (^C) sent"
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛑 Hold ^C",
                    color = AccentCrimson,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFontFamily
                )
            }
        }
    }

    // Modal Dialog for typing custom text without screen-squashing
    if (isTextDialogOpen) {
        AlertDialog(
            onDismissRequest = { isTextDialogOpen = false },
            title = {
                Text(text = "Send Input to Agent", color = TextPrimary)
            },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text("Enter prompt, instructions, or flags...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderDark
                    ),
                    maxLines = 4
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customText.isNotBlank()) {
                            onSendText(customText)
                            customText = ""
                            isTextDialogOpen = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    Text("Send ⏎", color = CanvasDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isTextDialogOpen = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun KeyButton(
    label: String,
    onClick: () -> Unit,
    minWidth: androidx.compose.ui.unit.Dp = 52.dp,
    isPrimary: Boolean = false
) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = minWidth)
            .background(
                if (isPrimary) AccentEmerald else SurfaceElevated,
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                if (isPrimary) AccentEmerald else BorderHighlight,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPrimary) CanvasDark else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
            fontFamily = TerminalFontFamily
        )
    }
}
