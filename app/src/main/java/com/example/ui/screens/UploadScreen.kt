package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.formatBytes
import com.example.ui.components.TransferProgressDialog
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentRed
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryTeal
import com.example.ui.viewmodel.TeleDriveViewModel
import com.example.util.DiagnosticLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    viewModel: TeleDriveViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val vaults by viewModel.vaults.collectAsStateWithLifecycle()
    val defaultVault by viewModel.defaultVault.collectAsStateWithLifecycle()
    val availableFolders by viewModel.folders.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val channelVerificationState by viewModel.channelVerificationState.collectAsStateWithLifecycle()
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSize by remember { mutableStateOf(0L) }
    var selectedMimeType by remember { mutableStateOf("") }

    var isEncrypted by remember { mutableStateOf(false) }
    var encryptionPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var targetFolder by remember { mutableStateOf("/") }
    var selectedVaultId by remember { mutableStateOf(defaultVault?.id ?: vaults.firstOrNull()?.id) }
    var uploadErrorDetail by remember { mutableStateOf<String?>(null) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }

    // Keep selectedVaultId in sync whenever vaults or defaultVault load
    LaunchedEffect(defaultVault, vaults) {
        if (selectedVaultId == null || vaults.none { it.id == selectedVaultId }) {
            selectedVaultId = defaultVault?.id ?: vaults.firstOrNull()?.id
        }
    }

    // Trigger retry logic to verify channel administrator status whenever vault changes
    LaunchedEffect(selectedVaultId) {
        if (selectedVaultId != null) {
            viewModel.verifyVaultAdminStatus(selectedVaultId)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            var name = "file_${System.currentTimeMillis()}"
            var size = 0L
            val type = context.contentResolver.getType(it) ?: "application/octet-stream"

            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) name = cursor.getString(nameIdx)
                    if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                }
            }
            selectedFileName = name
            selectedFileSize = size
            selectedMimeType = type
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("رفع ملف إلى السحابة 📤", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDiagnosticDialog = true },
                        modifier = Modifier.testTag("open_diagnostic_logs_button")
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = "سجل التشخيص", tint = PrimaryCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Warning banner if no vault is configured yet
            if (vaults.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "تنبيه: لا توجد قناة تخزين مربوطة حتى الآن!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "قبل الرفع، يرجى التوجه إلى شاشة (الحسابات) وإضافة قناة تيليجرام كخزينة تخزين وتعيينها كافتراضية لضمان وصول الملف بنجاح.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // File Selection Box
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            1.5.dp,
                            if (selectedUri != null) PrimaryCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { filePickerLauncher.launch("*/*") }
                        .testTag("file_picker_trigger_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (selectedUri != null) PrimaryCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedUri != null) Icons.Default.Description else Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = if (selectedUri != null) PrimaryCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        if (selectedUri == null) {
                            Text("اضغط لاختيار ملف من جهازك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("يدعم جميع أنواع الملفات (فيديو، صوت، صور، مستندات، أرشيف)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(selectedFileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("الحجم: ${formatBytes(selectedFileSize)} • الصيغة: $selectedMimeType", style = MaterialTheme.typography.bodySmall, color = SecondaryTeal)

                            val estimatedChunks = ((selectedFileSize + 18 * 1024 * 1024L - 1) / (18 * 1024 * 1024L)).coerceAtLeast(1)
                            if (estimatedChunks > 1) {
                                Text("🧩 سيتم تقسيم هذا الملف الكبير تلقائياً إلى $estimatedChunks أجزاء لضمان استقرار الرفع.", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                            }
                        }
                    }
                }
            }

            // Zero-Knowledge E2EE Encryption Section
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isEncrypted) AccentPurple else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(18.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = AccentPurple)
                                Column {
                                    Text("تشفير فائق Zero-Knowledge E2EE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("تشفير محلي AES-256-GCM قبل الإرسال لتيليجرام", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Switch(
                                checked = isEncrypted,
                                onCheckedChange = { isEncrypted = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = AccentPurple, checkedTrackColor = AccentPurple.copy(alpha = 0.4f)),
                                modifier = Modifier.testTag("e2ee_switch")
                            )
                        }

                        if (isEncrypted) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = encryptionPassword,
                                    onValueChange = { encryptionPassword = it },
                                    label = { Text("كلمة المرور الرئيسية للتشفير *") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("encryption_password_input")
                                )

                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    label = { Text("تأكيد كلمة المرور *") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (encryptionPassword.isNotBlank() && confirmPassword.isNotBlank() && encryptionPassword != confirmPassword) {
                                    Text("كلمات المرور غير متطابقة!", style = MaterialTheme.typography.labelSmall, color = AccentRed)
                                }

                                Text(
                                    "⚠️ لن يتمكن أي طرف (حتى تيليجرام نفسه) من فتح أو معاينة الملف بدون كلمة المرور هذه.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentPurple
                                )
                            }
                        }
                    }
                }
            }

            // Virtual Folder & Destination Vault
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("وجهة التخزين في السحابة 📁", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = targetFolder,
                            onValueChange = { targetFolder = it },
                            label = { Text("المجلد الافتراضي") },
                            placeholder = { Text("/ أو /Photos أو /Documents") },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = PrimaryCyan) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (vaults.isNotEmpty()) {
                            Text("قناة التخزين الوجهة:", style = MaterialTheme.typography.labelMedium)
                            vaults.forEach { v ->
                                val isSel = selectedVaultId == v.id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedVaultId = v.id }
                                        .border(
                                            1.dp,
                                            if (isSel) SecondaryTeal else Color.Transparent,
                                            RoundedCornerShape(10.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSel) SecondaryTeal.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Storage, contentDescription = null, tint = SecondaryTeal)
                                        Column {
                                            Text(v.channelTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text(v.channelId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Channel Administrator Status & Retry Verification Card
            item {
                val state = channelVerificationState
                val hasVault = vaults.isNotEmpty() && selectedVaultId != null

                if (hasVault) {
                    when {
                        state?.isVerifying == true -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PrimaryCyan.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = PrimaryCyan)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "جارٍ التحقق وتأكيد رتبة البوت كمسؤول في القناة... ⏳",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PrimaryCyan
                                        )
                                        Text(
                                            state.statusText.ifBlank { "يتم فحص الصلاحيات عبر TDLib / Telegram Bot API..." },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        state?.isConfirmedAdmin == true -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(24.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "جاهز للرفع: تم تأكيد صلاحيات الأدمن بنجاح ✅",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AccentGreen
                                        )
                                        Text(
                                            state.statusText.ifBlank { "البوت مسؤول ومصرح له بنشر الملفات في القناة" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(24.dp))
                                        Text(
                                            "تنبيه: يجب تأكيد صلاحيات الأدمن قبل تفعيل الرفع 🔒",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AccentRed
                                        )
                                    }
                                    Text(
                                        state?.statusText?.takeIf { it.isNotBlank() }
                                            ?: state?.error?.takeIf { it.isNotBlank() }
                                            ?: "تأكد من إضافة البوت كمسؤول (Admin) في القناة ومنحه صلاحية نشر الرسائل (Post Messages).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { selectedVaultId?.let { viewModel.verifyVaultAdminStatus(it) } },
                                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text("إعادة المحاولة والفحص 🔄", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        }
                                        TextButton(
                                            onClick = { showDiagnosticDialog = true }
                                        ) {
                                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text("سجل التشخيص 📋", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Upload Action Button
            item {
                val hasVault = vaults.isNotEmpty() && selectedVaultId != null
                val hasValidFile = selectedUri != null
                val hasValidPassword = !isEncrypted || (encryptionPassword.isNotBlank() && encryptionPassword == confirmPassword)
                val canUpload = hasVault && hasValidPassword && !isLoading

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            if (!hasValidFile) {
                                filePickerLauncher.launch("*/*")
                            } else {
                                selectedUri?.let { uri ->
                                    viewModel.uploadFile(
                                        fileUri = uri,
                                        virtualFolder = if (targetFolder.startsWith("/")) targetFolder else "/$targetFolder",
                                        isEncrypted = isEncrypted,
                                        encryptionPassword = encryptionPassword,
                                        targetVaultId = selectedVaultId,
                                        onError = { err ->
                                            uploadErrorDetail = err
                                        },
                                        onComplete = {
                                            onNavigateBack()
                                        }
                                    )
                                }
                            }
                        },
                        enabled = canUpload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_upload_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasValidFile) PrimaryCyan else SecondaryTeal
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                        } else {
                            Icon(
                                if (!hasValidFile) Icons.Default.FolderOpen else Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = if (!hasValidFile)
                                    "اختر ملفاً من جهازك للرفع 📁"
                                else if (isEncrypted)
                                    "تشفير ورفع الملف إلى السحابة 🔒"
                                else
                                    "رفع الملف إلى السحابة 🚀",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    if (!hasVault) {
                        Text(
                            text = "⚠️ لا يمكن الرفع بدون تحديد قناة تخزين مربوطة.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentRed
                        )
                    } else if (!hasValidFile) {
                        Text(
                            text = "ℹ️ اضغط على الزر أعلاه أو على المربع لاختيار ملف من جهازك والبدء في الرفع.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isEncrypted && (encryptionPassword.isBlank() || encryptionPassword != confirmPassword)) {
                        Text(
                            text = "⚠️ يرجى إدخال وتأكيد كلمة مرور التشفير E2EE بشكل متطابق.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentPurple
                        )
                    }
                }
            }
        }
    }

    // Active upload progress dialog
    uploadProgress?.let { prog ->
        TransferProgressDialog(progress = prog)
    }

    // Diagnostic Upload Error Dialog with Actionable Advice
    uploadErrorDetail?.let { err ->
        AlertDialog(
            onDismissRequest = { uploadErrorDetail = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed)
                    Text("فشل الرفع إلى تيليجرام ⚠️", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PrimaryCyan.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("💡 نصائح حل المشكلة السريع:", fontWeight = FontWeight.Bold, color = PrimaryCyan, style = MaterialTheme.typography.labelMedium)
                            Text("1. افتح قناتك في تيليجرام > المشرفون (Administrators).", style = MaterialTheme.typography.bodySmall)
                            Text("2. أضف البوت كمسؤول مع تفعيل صلاحية 'نشر الرسائل' (Post Messages).", style = MaterialTheme.typography.bodySmall)
                            Text("3. إذا كانت القناة خاصة، تأكد أن معرف القناة يبدأ بـ -100.", style = MaterialTheme.typography.bodySmall)
                            Text("4. يمكنك فحص وتأكيد القناة في شاشة 'إدارة الحسابات والقنوات'.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { uploadErrorDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text("حسناً فهمت ذلك", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        uploadErrorDetail = null
                        showDiagnosticDialog = true
                    }
                ) {
                    Text("سجل التشخيص 📋")
                }
            }
        )
    }

    // Diagnostic Logs Dialog
    if (showDiagnosticDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = PrimaryCyan)
                    Text("سجل تشخيص العمليات (TDLib & Streams)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الأحداث المسجلة (${diagnosticLogs.size}):", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = {
                                    val formatted = DiagnosticLogger.getFormattedLogs()
                                    clipboardManager.setText(AnnotatedString(formatted))
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("نسخ السجل", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(
                                onClick = { viewModel.clearDiagnosticLogs() }
                            ) {
                                Text("مسح", style = MaterialTheme.typography.labelSmall, color = AccentRed)
                            }
                        }
                    }

                    if (diagnosticLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا توجد سجلات مسجلة بعد.", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(diagnosticLogs.size) { index ->
                                val entry = diagnosticLogs[diagnosticLogs.size - 1 - index]
                                val levelColor = when (entry.level) {
                                    DiagnosticLogger.Level.DEBUG -> MaterialTheme.colorScheme.outline
                                    DiagnosticLogger.Level.INFO -> PrimaryCyan
                                    DiagnosticLogger.Level.SUCCESS -> AccentGreen
                                    DiagnosticLogger.Level.WARN -> SecondaryTeal
                                    DiagnosticLogger.Level.ERROR -> AccentRed
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "[${entry.tag}] ${entry.formattedTime}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = levelColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = entry.level.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = levelColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.size(4.dp))
                                        Text(
                                            text = entry.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (!entry.details.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text(
                                                text = entry.details,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDiagnosticDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text("إغلاق", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
