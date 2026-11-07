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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentremote.ui.theme.*
import kotlinx.coroutines.delay

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

    // Auto-dismiss the SIGINT feedback after 2.5 seconds
    LaunchedEffect(ctrlCMessage) {
        if (ctrlCMessage != null) {
            delay(2500)
            ctrlCMessage = null
        }
    }

    fun triggerHaptic(durationMs: Long = 35) {
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
            .border(1.dp, BorderDark, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(12.dp)
    ) {
        // Feedback Pill if Ctrl+C pressed
        AnimatedVisibility(visible = ctrlCMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(AccentCrimsonMuted, RoundedCornerShape(4.dp))
                    .padding(vertical = 6.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ctrlCMessage ?: "",
                    color = AccentCrimson,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = TerminalFontFamily,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Row 1: Split Thumb Zone (Left: Arrow Navigation | Right: Space / Enter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Thumb (Arrow Up, Arrow Down)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavIconButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Up",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        triggerHaptic(20)
                        onRawKey("UP")
                    }
                )
                NavIconButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Down",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        triggerHaptic(20)
                        onRawKey("DOWN")
                    }
                )
            }

            // Right Thumb (SPACE, ENTER)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyButton(label = "SPACE", minWidth = 76.dp, onClick = {
                    triggerHaptic(20)
                    onRawKey("SPACE")
                })
                KeyButton(
                    label = "ENTER",
                    minWidth = 76.dp,
                    isPrimary = true,
                    onClick = {
                        triggerHaptic(30)
                        onRawKey("ENTER")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: Custom Text Prompt Bar & Protected Ctrl+C Latch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collapsed Text Input Pill
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(SurfaceElevated, RoundedCornerShape(6.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                    .clickable { isTextDialogOpen = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Keyboard",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Custom input...",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = TerminalFontFamily
                )
            }

            // Protected Ctrl+C Latch (Requires 400ms Hold or informs user)
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .width(96.dp)
                    .background(AccentCrimsonMuted.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .border(1.dp, AccentCrimson.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                ctrlCMessage = "Hold to send SIGINT"
                            },
                            onLongPress = {
                                triggerHaptic(70)
                                onRawKey("SIGINT")
                                ctrlCMessage = "SIGINT sent"
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "^C (Hold)",
                    color = AccentCrimson,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = TerminalFontFamily,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }

    // Modal Dialog for typing custom text
    if (isTextDialogOpen) {
        AlertDialog(
            onDismissRequest = { isTextDialogOpen = false },
            title = {
                Text(
                    text = "Custom Input",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text("Type prompt or command...", color = TextMuted) },
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
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Send", color = CanvasDark, fontWeight = FontWeight.Bold)
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
private fun NavIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .width(52.dp)
            .background(SurfaceElevated, RoundedCornerShape(6.dp))
            .border(1.dp, BorderHighlight, RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@Composable
private fun KeyButton(
    label: String,
    onClick: () -> Unit,
    minWidth: androidx.compose.ui.unit.Dp = 64.dp,
    isPrimary: Boolean = false
) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = minWidth)
            .background(
                if (isPrimary) AccentEmerald else SurfaceElevated,
                RoundedCornerShape(6.dp)
            )
            .border(
                1.dp,
                if (isPrimary) AccentEmerald else BorderHighlight,
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPrimary) CanvasDark else TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TerminalFontFamily,
            letterSpacing = 0.5.sp
        )
    }
}
