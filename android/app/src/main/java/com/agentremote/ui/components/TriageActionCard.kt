package com.agentremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
            .border(1.dp, AccentAmber.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(AccentAmberMuted.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ACTION REQUIRED",
                        color = AccentAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TerminalFontFamily,
                        letterSpacing = 0.8.sp
                    )
                }

                Text(
                    text = "Dismiss",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = TerminalFontFamily,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Headline
            Text(
                text = prompt.headline,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Target File Pill
            if (!prompt.target.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(CanvasDark, RoundedCornerShape(4.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = prompt.target,
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bounded Diff Viewer
            if (!prompt.diffPreview.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val rawLines = prompt.diffPreview.lines()
                val maxVisibleLines = 20
                val displayLines = rawLines.take(maxVisibleLines)
                val remainingCount = rawLines.size - maxVisibleLines

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .background(CanvasDark, RoundedCornerShape(4.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
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
                    fontSize = 11.sp,
                    fontFamily = TerminalFontFamily
                )
            }

            // Action Buttons
            if (prompt.options.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                val primary = prompt.options.first()
                Button(
                    onClick = { onOptionSelected(primary.key) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentEmerald,
                        contentColor = CanvasDark
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "[ ${primary.key} ]  ${primary.label}",
                        fontWeight = FontWeight.Bold,
                        fontFamily = TerminalFontFamily,
                        fontSize = 13.sp
                    )
                }

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
                                    .height(40.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHighlight),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "[ ${opt.key} ] ${opt.label}",
                                    fontSize = 12.sp,
                                    fontFamily = TerminalFontFamily,
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
