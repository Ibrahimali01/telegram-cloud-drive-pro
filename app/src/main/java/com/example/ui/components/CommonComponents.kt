package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.DriveFileEntity
import com.example.data.model.TransferProgress
import com.example.data.model.formatBytes
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentRed
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.TelegramBlue

@Composable
fun FileTypeIcon(
    file: DriveFileEntity,
    modifier: Modifier = Modifier,
    iconSize: Int = 28
) {
    val (icon, bgGradient) = when {
        file.isEncrypted -> Pair(
            Icons.Default.Lock,
            Brush.linearGradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)))
        )
        file.mimeType.startsWith("image/") -> Pair(
            Icons.Default.Image,
            Brush.linearGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)))
        )
        file.mimeType.startsWith("video/") -> Pair(
            Icons.Default.Movie,
            Brush.linearGradient(listOf(Color(0xFFF7971E), Color(0xFFFFD200)))
        )
        file.mimeType.startsWith("audio/") -> Pair(
            Icons.Default.AudioFile,
            Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D)))
        )
        file.extension in listOf("zip", "rar", "7z", "tar", "gz") -> Pair(
            Icons.Default.FolderZip,
            Brush.linearGradient(listOf(Color(0xFFFF8008), Color(0xFFFFC837)))
        )
        file.mimeType.contains("pdf") || file.extension == "pdf" -> Pair(
            Icons.Default.Description,
            Brush.linearGradient(listOf(Color(0xFFE52D27), Color(0xFFB31217)))
        )
        else -> Pair(
            Icons.Default.InsertDriveFile,
            Brush.linearGradient(listOf(Color(0xFF2193B0), Color(0xFF6DD5ED)))
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgGradient)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "File Type",
            tint = Color.White,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

@Composable
fun TransferProgressDialog(
    progress: TransferProgress,
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = { /* Non-cancellable during active stream */ },
        confirmButton = {},
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = PrimaryCyan
                )
                Text(
                    text = if (progress.isEncrypting || progress.isDecrypting) "تشفير E2EE آمن 🔒" else "نقل بيانات تيليجرام ☁️",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = progress.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                LinearProgressIndicator(
                    progress = { progress.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = PrimaryCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${formatBytes(progress.bytesTransferred)} / ${formatBytes(progress.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(progress.progressFraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCyan
                    )
                }

                if (progress.totalChunks > 1) {
                    Text(
                        text = "الأجزاء المرفوعة: ${progress.currentChunk} من ${progress.totalChunks}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryTeal
                    )
                }

                if (progress.speedBytesPerSec > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Speed",
                            modifier = Modifier.size(14.dp),
                            tint = AccentGreen
                        )
                        Text(
                            text = "السرعة: ${progress.formattedSpeed}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentGreen
                        )
                    }
                }

                Text(
                    text = progress.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
fun PasswordPromptDialog(
    title: String = "فك تشفير الملف E2EE 🔒",
    description: String = "أدخل كلمة المرور السرية لفك تشفير هذا الملف وتنزيله بأمان:",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = description, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        isError = false
                    },
                    label = { Text("كلمة المرور") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input_field")
                )
                if (isError) {
                    Text(
                        text = "يرجى كتابة كلمة المرور",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.isNotBlank()) {
                        onConfirm(password)
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier.testTag("confirm_password_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
            ) {
                Text("تأكيد وفك التشفير", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun RenameFileDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعادة تسمية الملف ✏️", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("الاسم الجديد") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input_field")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) onConfirm(newName)
                },
                modifier = Modifier.testTag("confirm_rename_button")
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun MoveFileDialog(
    availableFolders: List<String>,
    currentFolder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFolder by remember { mutableStateOf(currentFolder) }
    var customFolder by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نقل إلى مجلد 📂", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اختر المجلد الوجهة:", style = MaterialTheme.typography.labelMedium)
                val allFlds = (listOf("/") + availableFolders).distinct()
                allFlds.forEach { fld ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFolder = fld },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedFolder == fld) PrimaryCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = PrimaryCyan)
                            Text(text = if (fld == "/") "المجلد الرئيسي (/)" else fld, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = customFolder,
                    onValueChange = { customFolder = it },
                    label = { Text("أو مجلد جديد...") },
                    placeholder = { Text("/Work/Projects") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = if (customFolder.isNotBlank()) {
                        if (customFolder.startsWith("/")) customFolder else "/$customFolder"
                    } else selectedFolder
                    onConfirm(target)
                },
                modifier = Modifier.testTag("confirm_move_button")
            ) {
                Text("نقل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
