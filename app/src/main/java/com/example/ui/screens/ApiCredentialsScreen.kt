package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentRed
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryTeal
import com.example.ui.viewmodel.TeleDriveViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiCredentialsScreen(
    viewModel: TeleDriveViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAccounts: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val savedApiId by viewModel.savedApiId.collectAsStateWithLifecycle()
    val savedApiHash by viewModel.savedApiHash.collectAsStateWithLifecycle()
    val savedAppTitle by viewModel.savedAppTitle.collectAsStateWithLifecycle()

    var apiIdInput by remember { mutableStateOf("") }
    var apiHashInput by remember { mutableStateOf("") }
    var appTitleInput by remember { mutableStateOf("") }

    var showGuide by remember { mutableStateOf(true) }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Sync from ViewModel on initial load
    LaunchedEffect(savedApiId, savedApiHash, savedAppTitle) {
        if (apiIdInput.isEmpty() && savedApiId.isNotEmpty()) {
            apiIdInput = savedApiId
        }
        if (apiHashInput.isEmpty() && savedApiHash.isNotEmpty()) {
            apiHashInput = savedApiHash
        }
        if (appTitleInput.isEmpty() && savedAppTitle.isNotEmpty()) {
            appTitleInput = savedAppTitle
        }
    }

    // Validation Logic
    val isApiIdValid by remember {
        derivedStateOf {
            val clean = apiIdInput.trim()
            clean.isNotEmpty() && clean.all { it.isDigit() } && clean.toLongOrNull()?.let { it > 0 } == true
        }
    }

    val isApiHashValid by remember {
        derivedStateOf {
            val clean = apiHashInput.trim()
            clean.length == 32 && clean.all { c -> (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F') }
        }
    }

    val canSave by remember {
        derivedStateOf { isApiIdValid && isApiHashValid }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بيانات Telegram API (my.telegram.org) 🔑", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Security badge header
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryCyan.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PrimaryCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryCyan)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تخزين مشفّر في عتاد الجهاز (Hardware KeyStore)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("يتم حفظ مفاتيح الـ API وتوكنات الجلسات في EncryptedSharedPreferences بتشفير AES-256-GCM لمنع أي وصول غير مصرح به واستمرارية الجلسة عبر إعادة التشغيل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Input Form Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("إعداد وتوثيق مفاتيح التطبيق الخاصة بك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        // App Title
                        OutlinedTextField(
                            value = appTitleInput,
                            onValueChange = { appTitleInput = it },
                            label = { Text("اسم التطبيق / Short Title") },
                            placeholder = { Text("TeleDrive Pro") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // API ID Field
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = apiIdInput,
                                onValueChange = { apiIdInput = it.trim() },
                                label = { Text("App API ID *") },
                                placeholder = { Text("مثال: 2040 أو 12345678") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                trailingIcon = {
                                    if (apiIdInput.isNotEmpty()) {
                                        if (isApiIdValid) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Valid", tint = AccentGreen)
                                        } else {
                                            Icon(Icons.Default.Close, contentDescription = "Invalid", tint = AccentRed)
                                        }
                                    }
                                },
                                isError = apiIdInput.isNotEmpty() && !isApiIdValid,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_id_input")
                            )

                            if (apiIdInput.isNotEmpty() && !isApiIdValid) {
                                Text(
                                    text = "يجب أن يكون API ID رقماً صحيحاً موجب (أرقام فقط)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentRed
                                )
                            }
                        }

                        // API HASH Field
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = apiHashInput,
                                onValueChange = { apiHashInput = it.trim() },
                                label = { Text("App API Hash * (32 حرف هكس)") },
                                placeholder = { Text("مثال: b18441a1ff607e10a989891a5462e627") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Done
                                ),
                                trailingIcon = {
                                    if (apiHashInput.isNotEmpty()) {
                                        if (isApiHashValid) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Valid", tint = AccentGreen)
                                        } else {
                                            Icon(Icons.Default.Close, contentDescription = "Invalid", tint = AccentRed)
                                        }
                                    }
                                },
                                isError = apiHashInput.isNotEmpty() && !isApiHashValid,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_hash_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (apiHashInput.isNotEmpty() && !isApiHashValid) {
                                    Text(
                                        text = "يجب أن يتكون API Hash من 32 حرف ست عشري (0-9, a-f)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentRed,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                Text(
                                    text = "${apiHashInput.length} / 32",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (apiHashInput.length == 32) AccentGreen else MaterialTheme.colorScheme.outline,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Save Success feedback
                        saveSuccessMessage?.let { msg ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AccentGreen.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen)
                                    Text(msg, style = MaterialTheme.typography.bodySmall, color = AccentGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.saveTelegramApiCredentials(
                                        apiId = apiIdInput,
                                        apiHash = apiHashInput,
                                        appTitle = appTitleInput
                                    )
                                    saveSuccessMessage = "تم حفظ المفاتيح في وحدة التخزين المشفرة بنجاح 🔐"
                                },
                                enabled = canSave,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_api_credentials_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("حفظ المفاتيح", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    viewModel.saveTelegramApiCredentials(
                                        apiId = apiIdInput,
                                        apiHash = apiHashInput,
                                        appTitle = appTitleInput
                                    )
                                    onNavigateToAccounts()
                                },
                                enabled = canSave,
                                colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("start_tdlib_with_api_button")
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("متابعة لربط الحسابات", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Clear credentials button
                        OutlinedButton(
                            onClick = {
                                apiIdInput = ""
                                apiHashInput = ""
                                appTitleInput = ""
                                viewModel.saveTelegramApiCredentials("", "", "")
                                saveSuccessMessage = "تم مسح المفاتيح 🗑️"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مسح الخانات والبدء من جديد")
                        }
                    }
                }
            }

            // How to get API ID and Hash Guide
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showGuide = !showGuide },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.HelpOutline, contentDescription = null, tint = SecondaryTeal)
                                Text("كيفية الحصول على API ID و API Hash من تيليجرام؟", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(if (showGuide) "إخفاء" else "عرض", style = MaterialTheme.typography.labelSmall, color = SecondaryTeal)
                        }

                        AnimatedVisibility(visible = showGuide) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("1. افتح موقع تيليجرام الرسمي لإدارة التطبيقات: https://my.telegram.org", style = MaterialTheme.typography.bodySmall)
                                Text("2. سجّل الدخول برقم هاتفك وتأكيد رمز التوثيق الذي يصلك في تطبيق تيليجرام.", style = MaterialTheme.typography.bodySmall)
                                Text("3. اضغط على خيار «API development tools».", style = MaterialTheme.typography.bodySmall)
                                Text("4. املأ اسم التطبيق (App title) والاسم المختصر (Short name) واضغط «Create application».", style = MaterialTheme.typography.bodySmall)
                                Text("5. انسخ قيمة `App api_id` وقيمة `App api_hash` وألصقهما في الخانات أعلاه.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = PrimaryCyan)

                                Spacer(modifier = Modifier.height(4.dp))

                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org"))
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("فتح موقع my.telegram.org 🌐")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
