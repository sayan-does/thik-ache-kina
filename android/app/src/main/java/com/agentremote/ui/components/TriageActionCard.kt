package com.agentremote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentremote.data.PromptData
import com.agentremote.ui.theme.*

@Composable
fun TriageActionCard(
    prompt: PromptData,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderHighlight, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            // Header: Category & Escape Hatch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTION APPROVAL REQUIRED",
                    color = AccentAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFontFamily,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Dismiss ✕",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Headline
            Text(
                text = prompt.headline,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Target File Pill (if extracted)
            if (!prompt.target.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(SurfaceElevated, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = prompt.target,
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bounded Diff Preview (Guaranteed not to push action buttons off screen)
            if (!prompt.diffPreview.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val rawLines = prompt.diffPreview.lines()
                val maxVisibleLines = 25
                val displayLines = rawLines.take(maxVisibleLines)
                val remainingCount = rawLines.size - maxVisibleLines

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .background(CanvasDark, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    displayLines.forEach { line ->
                        val color = when {
                            line.startsWith("+") -> AccentEmerald
                            line.startsWith("-") -> AccentCrimson
                            line.startsWith("@@") -> AccentCyan
                            else -> TextSecondary
                        }
                        Text(
                            text = line,
                            color = color,
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (remainingCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "... (+$remainingCount more lines)",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = TerminalFontFamily
                        )
                    }
                }
            }

            // Hint for Raw VT Mode
            if (!prompt.hint.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = prompt.hint,
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontFamily = TerminalFontFamily
                )
            }

            // Action Buttons / Option Chips (Prominent and immediately reachable)
            if (prompt.options.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                // Primary Option (First option, e.g. [1] Approve)
                val primary = prompt.options.first()
                Button(
                    onClick = { onOptionSelected(primary.key) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentEmerald,
                        contentColor = CanvasDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "[ ${primary.key} ]  ${primary.label}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Secondary Options (Remaining options, e.g. [2] Reject, [3] Edit)
                if (prompt.options.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (opt in prompt.options.drop(1)) {
                            OutlinedButton(
                                onClick = { onOptionSelected(opt.key) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHighlight),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "[ ${opt.key} ] ${opt.label}",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
