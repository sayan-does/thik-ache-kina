package com.agentremote.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentremote.data.UpdateInfo
import com.agentremote.data.UpdateManager
import com.agentremote.data.UpdateStatus
import com.agentremote.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateCard(
    currentVersion: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }

    var status by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var activeUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var requiresPermission by remember { mutableStateOf(false) }

    fun checkUpdates() {
        coroutineScope.launch {
            status = UpdateStatus.Checking
            status = updateManager.checkForUpdates(currentVersion)
            if (status is UpdateStatus.UpdateAvailable) {
                activeUpdateInfo = (status as UpdateStatus.UpdateAvailable).info
            }
        }
    }

    fun startDownload(info: UpdateInfo) {
        coroutineScope.launch {
            try {
                status = UpdateStatus.Downloading(0f, 0L, info.apkSize)
                val file = updateManager.downloadUpdate(info) { progress, downloaded, total ->
                    status = UpdateStatus.Downloading(progress, downloaded, total)
                }
                downloadedApk = file
                status = UpdateStatus.ReadyToInstall(file, info)
            } catch (e: Exception) {
                status = UpdateStatus.Error(e.message ?: "Download failed")
            }
        }
    }

    fun triggerInstall(file: File) {
        if (!updateManager.canInstallUnknownApps()) {
            requiresPermission = true
            Toast.makeText(context, "Grant 'Install Unknown Apps' permission in Settings", Toast.LENGTH_LONG).show()
            updateManager.openInstallPermissionSettings()
        } else {
            requiresPermission = false
            try {
                updateManager.installApk(file)
            } catch (e: Exception) {
                Toast.makeText(context, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Category Pill & Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(SurfaceElevated, RoundedCornerShape(4.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SOFTWARE UPDATE",
                        color = AccentCyan,
                        fontSize = 10.sp,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                when (status) {
                    is UpdateStatus.Checking -> {
                        Text(
                            text = "CHECKING...",
                            color = AccentAmber,
                            fontSize = 10.sp,
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is UpdateStatus.UpToDate -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentEmerald, RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = "UP TO DATE",
                                color = AccentEmerald,
                                fontSize = 10.sp,
                                fontFamily = TerminalFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    is UpdateStatus.UpdateAvailable -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentCyan, RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = "UPDATE AVAILABLE",
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontFamily = TerminalFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    is UpdateStatus.ReadyToInstall -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentEmerald, RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = "READY",
                                color = AccentEmerald,
                                fontSize = 10.sp,
                                fontFamily = TerminalFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "v$currentVersion",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily
                        )
                    }
                }
            }

            // Body: Information & Version details
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Application Updates",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Direct GitHub release distribution with 1-tap in-app download and package installation.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            // Version Comparison Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CanvasDark, RoundedCornerShape(6.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "INSTALLED",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v$currentVersion",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = TerminalFontFamily
                    )
                }

                val latestText = when (val s = status) {
                    is UpdateStatus.UpdateAvailable -> s.info.tagName
                    is UpdateStatus.ReadyToInstall -> s.info.tagName
                    is UpdateStatus.UpToDate -> "v${s.currentVersion}"
                    else -> "—"
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "LATEST RELEASE",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = latestText,
                        color = if (status is UpdateStatus.UpdateAvailable) AccentCyan else TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = TerminalFontFamily,
                        fontWeight = if (status is UpdateStatus.UpdateAvailable) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Dynamic Action View based on status
            when (val s = status) {
                is UpdateStatus.Idle -> {
                    OutlinedButton(
                        onClick = { checkUpdates() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHighlight),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Check",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Check for Updates",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = TerminalFontFamily
                        )
                    }
                }

                is UpdateStatus.Checking -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = AccentCyan,
                        trackColor = BorderDark
                    )
                }

                is UpdateStatus.UpToDate -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "You are running the latest version.",
                            color = AccentEmerald,
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily
                        )
                        TextButton(
                            onClick = { checkUpdates() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Check Again",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = TerminalFontFamily
                            )
                        }
                    }
                }

                is UpdateStatus.UpdateAvailable -> {
                    val info = s.info
                    val mbSize = String.format("%.1f MB", info.apkSize / (1024f * 1024f))

                    // Changelog preview card
                    if (info.releaseNotes.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CanvasDark, RoundedCornerShape(6.dp))
                                .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "CHANGELOG (${info.tagName}):",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = TerminalFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = info.releaseNotes.take(300),
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = TerminalFontFamily,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Button(
                        onClick = { startDownload(info) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = CanvasDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Download & Install Update ($mbSize)",
                            color = CanvasDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 0.3.sp
                        )
                    }
                }

                is UpdateStatus.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val downloadedMb = String.format("%.1f", s.downloadedBytes / (1024f * 1024f))
                        val totalMb = String.format("%.1f MB", s.totalBytes / (1024f * 1024f))
                        val percent = (s.progress * 100).toInt()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading update ($percent%)...",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontFamily = TerminalFontFamily
                            )
                            Text(
                                text = "$downloadedMb / $totalMb",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = TerminalFontFamily
                            )
                        }

                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = AccentCyan,
                            trackColor = BorderDark,
                        )
                    }
                }

                is UpdateStatus.ReadyToInstall -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (requiresPermission) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AccentAmberMuted.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .border(1.dp, AccentAmber.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Permission",
                                    tint = AccentAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Android requires 'Install Unknown Apps' permission.",
                                    color = AccentYellow,
                                    fontSize = 11.sp,
                                    fontFamily = TerminalFontFamily
                                )
                            }
                        }

                        Button(
                            onClick = { triggerInstall(s.apkFile) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Install",
                                tint = CanvasDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Install Update (${s.info.tagName})",
                                color = CanvasDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }

                is UpdateStatus.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = s.message,
                            color = AccentCrimson,
                            fontSize = 11.sp,
                            fontFamily = TerminalFontFamily
                        )
                        OutlinedButton(
                            onClick = { checkUpdates() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Retry Check",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = TerminalFontFamily
                            )
                        }
                    }
                }
            }
        }
    }
}
