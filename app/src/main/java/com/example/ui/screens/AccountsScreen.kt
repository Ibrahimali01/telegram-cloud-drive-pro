package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.DriveVaultEntity
import com.example.data.db.TelegramAccountEntity
import com.example.data.model.formatBytes
import com.example.data.repository.ChannelTestResult
import com.example.tdlib.TdlibValidators
import com.example.ui.components.QrCodeView
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentRed
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryTeal
import com.example.ui.viewmodel.TeleDriveViewModel
import org.drinkless.tdlib.TdApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: TeleDriveViewModel,
    onNavigateBack: () -> Unit,
    onNavigateApiCredentials: () -> Unit = {},
    onNavigateUpload: () -> Unit = {}
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val vaults by viewModel.vaults.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val tdlibAuthState by viewModel.tdlibAuthState.collectAsStateWithLifecycle()
    val inlineError by viewModel.authInlineError.collectAsStateWithLifecycle()
    val resendCountdown by viewModel.resendCountdown.collectAsStateWithLifecycle()
    val floodWaitCountdown by viewModel.floodWaitCountdown.collectAsStateWithLifecycle()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showAddVaultDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var attachingBotAccount by remember { mutableStateOf<TelegramAccountEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "إدارة الحسابات وجلسات TDLib",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateApiCredentials) {
                        Icon(Icons.Default.Key, contentDescription = "API Keys", tint = PrimaryCyan)
                    }
                    IconButton(onClick = { showTutorialDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Tutorial", tint = SecondaryTeal)
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
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // TDLib Engine & Keystore Security Banner
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryCyan.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PrimaryCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(32.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("محرك TDLib الأصلي المشفر بـ Android Keystore", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "جميع الجلسات تعمل بمحرك TDLib الأصلي مع تشفير قواعد البيانات محلياً (AES-256-GCM) ولا يتم حفظ أي حساب إلا بعد تأكيد خوادم تيليجرام.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Telegram Accounts Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "الحسابات المتصلة (${accounts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Button(
                        onClick = {
                            viewModel.clearAuthInlineError()
                            showAddAccountDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_account_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("ربط حساب", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (accounts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                            Text("لا توجد حسابات أو جلسات TDLib نشطة حالياً", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("اضغط على 'ربط حساب TDLib' لتسجيل الدخول برقم الهاتف، البوت، أو رمز QR.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(accounts, key = { "account_${it.id}" }) { acc ->
                    AccountCard(
                        account = acc,
                        onSetPrimary = { viewModel.setPrimaryAccount(acc.id) },
                        onDelete = { viewModel.deleteAccount(acc) },
                        onAttachBot = { attachingBotAccount = acc }
                    )
                }
            }

            // Storage Channels / Vaults Section
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "قنوات التخزين (${vaults.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Button(
                        onClick = {
                            if (accounts.isNotEmpty()) {
                                showAddVaultDialog = true
                            } else {
                                showAddAccountDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_vault_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("إضافة قناة", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (vaults.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                            Text("لا توجد قنوات تخزين مضافة", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("أضف قناة تيليجرام خاصة لاستخدامها كخزينة سحابية لرفع وتنزيل الملفات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(vaults, key = { "vault_${it.id}" }) { vlt ->
                    VaultCard(
                        vault = vlt,
                        onSetDefault = { viewModel.setDefaultVault(vlt.id) },
                        onDelete = { viewModel.deleteVault(vlt) },
                        onUpload = {
                            viewModel.setDefaultVault(vlt.id)
                            onNavigateUpload()
                        },
                        onSync = { viewModel.syncVaultFiles(vlt.id) }
                    )
                }

                item {
                    Button(
                        onClick = onNavigateUpload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("accounts_screen_upload_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "الذهاب لرفع ملف إلى السحابة الآن 📤",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }

    // TDLib Real Login Dialog
    if (showAddAccountDialog) {
        RealTdlibLoginDialog(
            isLoading = isLoading,
            tdlibAuthState = tdlibAuthState,
            inlineError = inlineError,
            resendCountdown = resendCountdown,
            floodWaitCountdown = floodWaitCountdown,
            onDismiss = {
                viewModel.cancelTdlibLogin()
                showAddAccountDialog = false
            },
            onPhoneLogin = { apiId, apiHash, phone, onCodeSent ->
                viewModel.startTdlibPhoneAuth(apiId, apiHash, phone, onCodeSent)
            },
            onSubmitCode = { code, apiId, apiHash, label, onSuccess ->
                viewModel.submitTdlibCode(code, apiId, apiHash, label, onSuccess)
            },
            onSubmit2Fa = { password, apiId, apiHash, label, onSuccess ->
                viewModel.submitTdlib2FaPassword(password, apiId, apiHash, label, onSuccess)
            },
            onBotLogin = { apiId, apiHash, token, label, onSuccess ->
                viewModel.startTdlibBotAuth(apiId, apiHash, token, label, onSuccess)
            },
            onDirectBotLogin = { token, label, onSuccess ->
                viewModel.addBotAccountDirect(token, label) {
                    showAddAccountDialog = false
                    onSuccess()
                }
            },
            onStartQr = { apiId, apiHash ->
                viewModel.startTdlibQrAuth(apiId, apiHash)
            },
            onResendCode = {
                viewModel.resendTdlibCode()
            },
            onFinalizeReady = { apiId, apiHash, botToken, label ->
                viewModel.finalizeAccount(apiId, apiHash, botToken, label) {
                    showAddAccountDialog = false
                }
            }
        )
    }

    // Add Vault / Channel Dialog
    if (showAddVaultDialog) {
        AddVaultDialog(
            accounts = accounts,
            isLoading = isLoading,
            onDismiss = { showAddVaultDialog = false },
            onTestChannel = { channelId, accountId, explicitBotToken, onResult ->
                viewModel.testChannelConnection(accountId, channelId, explicitBotToken, onResult)
            },
            onConfirm = { channelId, title, accountId, isEncrypted, explicitBotToken ->
                viewModel.addVault(channelId, title, accountId, isEncrypted, explicitBotToken) {
                    showAddVaultDialog = false
                }
            }
        )
    }

    // Attach Bot Token Dialog for personal/phone accounts
    attachingBotAccount?.let { acc ->
        AttachBotTokenDialog(
            accountName = acc.accountName,
            isLoading = isLoading,
            onDismiss = { attachingBotAccount = null },
            onConfirm = { tok ->
                viewModel.updateAccountBotToken(acc.id, tok) {
                    attachingBotAccount = null
                }
            }
        )
    }

    // Tutorial Dialog
    if (showTutorialDialog) {
        TutorialDialog(onDismiss = { showTutorialDialog = false })
    }
}

@Composable
private fun AccountCard(
    account: TelegramAccountEntity,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit,
    onAttachBot: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (account.isPrimary) PrimaryCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (account.isBot) PrimaryCyan.copy(alpha = 0.2f) else AccentPurple.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (account.isBot) Icons.Default.SmartToy else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (account.isBot) PrimaryCyan else AccentPurple
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(account.accountName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (account.isPrimary) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PrimaryCyan.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("رئيسي", style = MaterialTheme.typography.labelSmall, color = PrimaryCyan, fontSize = 10.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (account.isBot) SecondaryTeal.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (account.isBot) "بوت Bot" else "جلسة TDLib",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (account.isBot) SecondaryTeal else AccentGreen,
                            fontSize = 10.sp
                        )
                    }
                }
                Text(
                    text = if (account.username.isNotBlank()) "@${account.username}" else (account.phoneNumber ?: "حساب مستخدم"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "ID: ${if (account.telegramUserId > 0) account.telegramUserId else account.id} • ${if (account.isBot) "Bot API" else (account.phoneNumber ?: "MTProto Active")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!account.isBot && account.botToken.isBlank()) {
                    IconButton(onClick = onAttachBot) {
                        Icon(Icons.Default.SmartToy, contentDescription = "ربط رمز بوت", tint = SecondaryTeal)
                    }
                }
                if (!account.isPrimary) {
                    IconButton(onClick = onSetPrimary) {
                        Icon(Icons.Default.Star, contentDescription = "Set Primary", tint = MaterialTheme.colorScheme.outline)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed)
                }
            }
        }
    }
}

@Composable
private fun VaultCard(
    vault: DriveVaultEntity,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
    onUpload: () -> Unit,
    onSync: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (vault.isDefault) SecondaryTeal else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (vault.isEncrypted) AccentPurple.copy(alpha = 0.2f) else SecondaryTeal.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (vault.isEncrypted) Icons.Default.Lock else Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (vault.isEncrypted) AccentPurple else SecondaryTeal
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(vault.channelTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (vault.isDefault) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SecondaryTeal.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("الافتراضية للرفع", style = MaterialTheme.typography.labelSmall, color = SecondaryTeal, fontSize = 10.sp)
                            }
                        }
                    }
                    Text("المعرف: ${vault.channelId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${vault.totalFiles} ملفات • ${formatBytes(vault.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!vault.isDefault) {
                        IconButton(onClick = onSetDefault) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Set Default", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed)
                    }
                }
            }

            // Quick Upload & Sync Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onUpload,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).testTag("vault_card_upload_${vault.id}")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "رفع ملف 📤",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Button(
                    onClick = onSync,
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("vault_card_sync_${vault.id}")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "مزامنة 🔄",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/**
 * Real TDLib Authentication Dialog:
 * Supports Phone (E.164 + dynamic length OTP + 2FA), Bot (format validated),
 * Session String (format inspection & safe unsupported rejection), and QR Code.
 */
@Composable
private fun RealTdlibLoginDialog(
    isLoading: Boolean,
    tdlibAuthState: TdApi.AuthorizationState?,
    inlineError: String?,
    resendCountdown: Int,
    floodWaitCountdown: Int,
    onDismiss: () -> Unit,
    onPhoneLogin: (apiId: String, apiHash: String, phone: String, onCodeSent: () -> Unit) -> Unit,
    onSubmitCode: (code: String, apiId: String, apiHash: String, label: String, onSuccess: () -> Unit) -> Unit,
    onSubmit2Fa: (password: String, apiId: String, apiHash: String, label: String, onSuccess: () -> Unit) -> Unit,
    onBotLogin: (apiId: String, apiHash: String, token: String, label: String, onSuccess: () -> Unit) -> Unit,
    onDirectBotLogin: (token: String, label: String, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
    onStartQr: (apiId: String, apiHash: String) -> Unit,
    onResendCode: () -> Unit,
    onFinalizeReady: (apiId: String, apiHash: String, botToken: String, label: String) -> Unit
) {
    // Tabs: 0: Phone, 1: Bot, 2: QR, 3: Session String
    var selectedTab by remember { mutableIntStateOf(0) }

    // API Credentials & Account Label
    var apiId by remember { mutableStateOf("2040") }
    var apiHash by remember { mutableStateOf("b18441a1ff607e10a989891a5462e627") }
    var isApiHashVisible by remember { mutableStateOf(false) }
    var accountLabel by remember { mutableStateOf("") }

    // Phone & OTP & 2FA State
    var phoneInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var password2FaInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Bot State
    var botTokenInput by remember { mutableStateOf("") }

    // Session String State
    var sessionStringInput by remember { mutableStateOf("") }

    // Auto-finalize when TDLib enters Ready
    LaunchedEffect(tdlibAuthState) {
        if (tdlibAuthState is TdApi.AuthorizationStateReady) {
            val botTok = if (selectedTab == 1) botTokenInput else ""
            onFinalizeReady(apiId, apiHash, botTok, accountLabel)
        }
    }

    // Trigger QR start when switching to QR tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == 2 && tdlibAuthState !is TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
            if (TdlibValidators.isValidApiId(apiId) && TdlibValidators.isValidApiHash(apiHash)) {
                onStartQr(apiId, apiHash)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "ربط حساب تيليجرام (TDLib) 🔐",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("الهاتف") },
                        icon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("بوت") },
                        icon = { Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("رمز QR") },
                        icon = { Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Session String") },
                        icon = { Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section 1: API ID & API Hash (Requirement 1 & 9)
                // Shown on initial steps
                val isInAuthStep = tdlibAuthState is TdApi.AuthorizationStateWaitCode ||
                        tdlibAuthState is TdApi.AuthorizationStateWaitPassword ||
                        tdlibAuthState is TdApi.AuthorizationStateWaitOtherDeviceConfirmation

                if (!isInAuthStep) {
                    // API ID warning if 2040 (Telegram Desktop ID)
                    if (TdlibValidators.isDesktopApiId(apiId)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "تنبيه: المعرف 2040 هو API ID الرسمي لـ Telegram Desktop. استخدامه في تطبيقات الطرف الثالث محفوف بالمخاطر وقد يعرض حسابك للتقييد. يوصى بإنشاء API ID خاص بك مجاناً عبر my.telegram.org.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // API ID: digits only, positive int
                        OutlinedTextField(
                            value = apiId,
                            onValueChange = { newVal ->
                                if (newVal.all { it.isDigit() }) apiId = newVal
                            },
                            label = { Text("API ID *") },
                            isError = !TdlibValidators.isValidApiId(apiId),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("api_id_input")
                        )

                        // API Hash: 32 hex chars, masked (Req 1 & 9)
                        OutlinedTextField(
                            value = apiHash,
                            onValueChange = { apiHash = it.trim() },
                            label = { Text("API Hash *") },
                            isError = !TdlibValidators.isValidApiHash(apiHash),
                            visualTransformation = if (isApiHashVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isApiHashVisible = !isApiHashVisible }) {
                                    Icon(
                                        imageVector = if (isApiHashVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Hash Visibility"
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f).testTag("api_hash_input")
                        )
                    }

                    // Optional local account label (Req 8)
                    OutlinedTextField(
                        value = accountLabel,
                        onValueChange = { accountLabel = it },
                        label = { Text("تسمية الحساب (اختياري)") },
                        placeholder = { Text("سحابتي الشخصية (سيتم حفظ الاسم الحقيقي)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("account_label_input")
                    )
                }

                // Inline Error Display (In Arabic)
                if (!inlineError.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                            Text(
                                text = inlineError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Flood Wait Countdown Display
                if (floodWaitCountdown > 0) {
                    Text(
                        text = "يرجى الانتظار $floodWaitCountdown ثانية قبل إعادة المحاولة (Flood Wait)... ⏳",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                // TAB 0: PHONE FLOW
                if (selectedTab == 0) {
                    when (val state = tdlibAuthState) {
                        is TdApi.AuthorizationStateWaitCode -> {
                            // Verification Code Step (Req 3)
                            val codeInfo = state.codeInfo
                            val requiredLength = TdlibValidators.getRequiredCodeLength(codeInfo)

                            Text(
                                text = "أدخل رمز التحقق (مكون من $requiredLength أرقام) المرسل إلى حساب تيليجرام:",
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryCyan,
                                fontWeight = FontWeight.Medium
                            )

                            OutlinedTextField(
                                value = codeInput,
                                onValueChange = { newVal ->
                                    if (newVal.all { it.isDigit() } && newVal.length <= requiredLength) {
                                        codeInput = newVal
                                    }
                                },
                                label = { Text("رمز التحقق ($requiredLength أرقام) *") },
                                placeholder = { Text("•".repeat(requiredLength)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("verification_code_input")
                            )

                            // Resend Code Countdown (Req 3)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (resendCountdown > 0) {
                                    Text(
                                        text = "إعادة إرسال الرمز بعد ($resendCountdown) ثانية",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    TextButton(
                                        onClick = onResendCode,
                                        enabled = !isLoading && floodWaitCountdown == 0
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("إعادة إرسال الرمز 🔄", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        is TdApi.AuthorizationStateWaitPassword -> {
                            // 2FA Password Step (Req 4)
                            Text(
                                text = "الحساب محمي بالتحقق بخطوتين (2FA Cloud Password). أدخل كلمة المرور للمتابعة:",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentPurple,
                                fontWeight = FontWeight.Medium
                            )

                            if (state.passwordHint.isNotBlank()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = 0.1f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "💡 تلميح كلمة المرور: ${state.passwordHint}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AccentPurple,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = password2FaInput,
                                onValueChange = { password2FaInput = it },
                                label = { Text("كلمة مرور 2FA *") },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Password Visibility"
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("2fa_password_input")
                            )
                        }
                        else -> {
                            // Phone input Step (Req 2)
                            Text(
                                text = "أدخل رقم هاتفك الدولي المسجل في تيليجرام بصيغة E.164:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("رقم الهاتف الدولي (E.164) *") },
                                placeholder = { Text("+966501234567 أو +201234567890") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = PrimaryCyan) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("phone_number_input")
                            )
                        }
                    }
                }

                // TAB 1: BOT TOKEN FLOW (Req 5)
                if (selectedTab == 1) {
                    Text(
                        text = "أدخل رمز Bot Token الحقيقي الصادر من @BotFather:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = botTokenInput,
                        onValueChange = { botTokenInput = it.trim() },
                        label = { Text("Bot Token *") },
                        placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ") },
                        isError = botTokenInput.isNotEmpty() && !TdlibValidators.isValidBotToken(botTokenInput),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("bot_token_input")
                    )

                    if (botTokenInput.isNotEmpty() && !TdlibValidators.isValidBotToken(botTokenInput)) {
                        Text(
                            text = "تنسيق الرمز غير صالح (يجب أن يكون 6-10 أرقام ثم : ثم 35 حرفاً).",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentRed
                        )
                    }
                }

                // TAB 2: QR CODE AUTHENTICATION (Req 7)
                if (selectedTab == 2) {
                    when (val state = tdlibAuthState) {
                        is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "امسح رمز QR من تطبيق تيليجرام الرسمي على هاتفك:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCyan
                                )

                                QrCodeView(
                                    data = state.link,
                                    size = 200.dp
                                )

                                Text(
                                    text = "الإعدادات > الأجهزة > ربط جهاز جديد 📱",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = "في انتظار المصادقة من الهاتف... ⏳",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryTeal
                                )
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(color = PrimaryCyan, modifier = Modifier.size(32.dp))
                                Text("جاري توليد رمز QR عبر TDLib...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // TAB 3: SESSION STRING FLOW (Req 6)
                if (selectedTab == 3) {
                    Text(
                        text = "فحص ومعاينة الجلسة النصية (Session String):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = sessionStringInput,
                        onValueChange = { sessionStringInput = it.trim() },
                        label = { Text("Session String") },
                        placeholder = { Text("1ApWapzMBu4... (Telethon/Pyrogram)") },
                        modifier = Modifier.fillMaxWidth().testTag("session_string_input")
                    )

                    // Format detections & Routing (Req 6)
                    if (TdlibValidators.isTelegramLink(sessionStringInput)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "⚠️ هذا رابط تيليجرام وليس رمز جلسة (Session String)!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else if (TdlibValidators.isValidBotToken(sessionStringInput)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SecondaryTeal.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "🤖 هذا رمز بوت (Bot Token) وليس رمز جلسة شخصية!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryTeal,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = {
                                        botTokenInput = sessionStringInput
                                        selectedTab = 1
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal)
                                ) {
                                    Text("الانتقال إلى تبويب البوت للربط", color = Color.Black)
                                }
                            }
                        }
                    }

                    // Transparent TDLib Limitations Disclosure (Req 6)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(18.dp))
                                Text("غير مدعوم في محرك TDLib الرسمي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AccentPurple)
                            }
                            Text(
                                text = "محرك TDLib الرسمي يعتمد على قواعد بيانات SQLite محلية مشفرة بمفاتيح مصادقة أصلية ولا يوفر دالة لاستيراد الجلسات النصية (Telethon/Pyrogram StringSession) مباشرة لأسباب أمنية. لتجنب إيهام المستخدمين بنجاح زائف، هذه الخاصية معطلة هنا. يرجى تسجيل الدخول برقم الهاتف أو برمز QR.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (selectedTab) {
                0 -> {
                    // Phone flow button depending on TDLib state
                    when (tdlibAuthState) {
                        is TdApi.AuthorizationStateWaitCode -> {
                            val reqLen = TdlibValidators.getRequiredCodeLength((tdlibAuthState as TdApi.AuthorizationStateWaitCode).codeInfo)
                            Button(
                                onClick = {
                                    onSubmitCode(codeInput, apiId, apiHash, accountLabel) {}
                                },
                                enabled = !isLoading && codeInput.length == reqLen && floodWaitCountdown == 0,
                                modifier = Modifier.testTag("confirm_code_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                                } else {
                                    Text("تأكيد الرمز والدخول ✅")
                                }
                            }
                        }
                        is TdApi.AuthorizationStateWaitPassword -> {
                            Button(
                                onClick = {
                                    onSubmit2Fa(password2FaInput, apiId, apiHash, accountLabel) {}
                                },
                                enabled = !isLoading && password2FaInput.isNotBlank() && floodWaitCountdown == 0,
                                modifier = Modifier.testTag("confirm_2fa_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                                } else {
                                    Text("تأكيد 2FA والدخول 🔐")
                                }
                            }
                        }
                        else -> {
                            val isPhoneValid = TdlibValidators.isValidPhoneE164(phoneInput)
                            val isApiValid = TdlibValidators.isValidApiId(apiId) && TdlibValidators.isValidApiHash(apiHash)
                            Button(
                                onClick = {
                                    onPhoneLogin(apiId, apiHash, phoneInput) {}
                                },
                                enabled = !isLoading && isPhoneValid && isApiValid && floodWaitCountdown == 0,
                                modifier = Modifier.testTag("send_phone_code_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                                } else {
                                    Text("إرسال رمز التحقق 📩")
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Bot flow buttons: Direct BotFather link OR TDLib MTProto link
                    val isBotValid = TdlibValidators.isValidBotToken(botTokenInput)
                    val isApiValid = TdlibValidators.isValidApiId(apiId) && TdlibValidators.isValidApiHash(apiHash)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onDirectBotLogin(botTokenInput, accountLabel) {}
                            },
                            enabled = !isLoading && isBotValid,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                            modifier = Modifier.testTag("confirm_bot_direct_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                            } else {
                                Text("ربط مباشر ⚡", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onBotLogin(apiId, apiHash, botTokenInput, accountLabel) {}
                            },
                            enabled = !isLoading && isBotValid && isApiValid,
                            modifier = Modifier.testTag("confirm_bot_button")
                        ) {
                            Text("عبر TDLib 🤖")
                        }
                    }
                }
                2 -> {
                    // QR refresh button
                    Button(
                        onClick = { onStartQr(apiId, apiHash) },
                        enabled = !isLoading && TdlibValidators.isValidApiId(apiId) && TdlibValidators.isValidApiHash(apiHash)
                    ) {
                        Text("تحديث رمز QR 🔄")
                    }
                }
                3 -> {
                    // Session String: Disabled per Req 6
                    Button(
                        onClick = {},
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("غير مدعوم في TDLib ❌", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_login_button")
            ) {
                Text(
                    text = if (tdlibAuthState is TdApi.AuthorizationStateWaitCode || tdlibAuthState is TdApi.AuthorizationStateWaitPassword) "إلغاء والعودة" else "إلغاء"
                )
            }
        }
    )
}

/**
 * Add Vault / Storage Channel Dialog with Channel ID format validation, testing, and Account selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVaultDialog(
    accounts: List<TelegramAccountEntity>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onTestChannel: (channelId: String, accountId: Long, explicitBotToken: String?, onResult: (ChannelTestResult) -> Unit) -> Unit,
    onConfirm: (channelId: String, title: String, accountId: Long, isEncrypted: Boolean, explicitBotToken: String?) -> Unit
) {
    var channelId by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var explicitBotToken by remember { mutableStateOf("") }
    var isEncrypted by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ChannelTestResult?>(null) }

    val currentAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val normalizedId = remember(channelId) {
        com.example.data.repository.TeleDriveRepository.normalizeChannelId(channelId)
    }

    val isChannelValid = normalizedId.startsWith("-100") || normalizedId.startsWith("@")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة قناة تخزين (خزينة) 📁", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        if (currentAccount?.isBot == true) {
                            "أضف البوت كمسؤول (Admin) في قناتك مع صلاحية نشر الرسائل، ثم أدخل معرف القناة أو رابطها:"
                        } else {
                            "أدخل معرف أو رابط قناتك على تيليجرام (يجب أن تكون أنت صاحب القناة أو مشرفاً يمتلك صلاحية النشر):"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Account Selector
                if (accounts.isNotEmpty()) {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = isDropdownExpanded,
                            onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = "${currentAccount?.accountName ?: ""} (@${currentAccount?.username ?: ""})",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("الحساب المالك للقناة") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false }
                            ) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.accountName} (@${acc.username}) ${if (acc.isBot) "🤖 [بوت]" else "👤 [مستخدم]"}") },
                                        onClick = {
                                            selectedAccountId = acc.id
                                            isDropdownExpanded = false
                                            testResult = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Account Type & Ownership Info (No Bot Token required for user phone/QR accounts!)
                item {
                    if (currentAccount != null && !currentAccount.isBot) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PrimaryCyan.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryCyan)
                                Column {
                                    Text("حساب مستخدم تيليجرام المالك 👤", fontWeight = FontWeight.Bold, color = PrimaryCyan, style = MaterialTheme.typography.labelMedium)
                                    Text("سيتم التحقق من ملكيتك أو رتبتك كمسؤول في القناة مباشرة عبر حسابك (${currentAccount.accountName}) بدون الحاجة إلى أي بوت.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else if (currentAccount != null && currentAccount.isBot) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SecondaryTeal.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = SecondaryTeal)
                                    Text("حساب بوت تيليجرام 🤖", fontWeight = FontWeight.Bold, color = SecondaryTeal, style = MaterialTheme.typography.labelMedium)
                                }
                                Text("تأكد من فتح قناتك في تيليجرام وإضافة البوت (@${currentAccount.username}) كمسؤول مع صلاحية نشر الرسائل.", style = MaterialTheme.typography.bodySmall)
                                if (currentAccount.botToken.isBlank()) {
                                    OutlinedTextField(
                                        value = explicitBotToken,
                                        onValueChange = { explicitBotToken = it.trim(); testResult = null },
                                        label = { Text("رمز البوت (Bot Token) *") },
                                        placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = channelId,
                        onValueChange = { 
                            channelId = it.trim()
                            testResult = null 
                        },
                        label = { Text("معرف أو رابط القناة *") },
                        placeholder = { Text("رابط القناة https://t.me/... أو -1001234567890") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_channel_id_input")
                    )
                }

                if (normalizedId.isNotBlank()) {
                    item {
                        Text(
                            text = "المعرف المعتمد للتخزين: $normalizedId",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryTeal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان الخزينة (اختياري)") },
                        placeholder = { Text("خزينة الفيديوهات والمستندات") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Test Channel Button
                item {
                    Button(
                        onClick = {
                            isTesting = true
                            testResult = null
                            onTestChannel(channelId, selectedAccountId, explicitBotToken.ifBlank { null }) { res ->
                                isTesting = false
                                testResult = res
                                if (res.isSuccess && title.isBlank() && res.channelTitle.isNotBlank()) {
                                    title = res.channelTitle
                                }
                            }
                        },
                        enabled = !isTesting && channelId.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("جارٍ فحص القناة والتأكد من الصلاحيات...", color = Color.Black)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                if (currentAccount?.isBot == true) "فحص وتأكيد رتبة البوت كمسؤول 🔍"
                                else "فحص وتأكيد ملكية القناة من تيليجرام 🔍",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Test Result Feedback
                testResult?.let { res ->
                    item {
                        if (res.isSuccess) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                                    Column {
                                        Text("تم التحقق بنجاح! تم تأكيد صلاحياتك بالقناة 🚀", fontWeight = FontWeight.Bold, color = AccentGreen, style = MaterialTheme.typography.bodySmall)
                                        Text("اسم القناة المعتمد: ${res.channelTitle} (${res.normalizedChannelId})", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.12f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("⚠️ نتيجة الفحص: تعذر اعتماد القناة", fontWeight = FontWeight.Bold, color = AccentRed, style = MaterialTheme.typography.labelSmall)
                                    Text(res.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("خزينة مشفرة E2EE:", fontWeight = FontWeight.Bold)
                            Text("تشفير AES-256-GCM محلي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(checked = isEncrypted, onCheckedChange = { isEncrypted = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (channelId.isNotBlank() && selectedAccountId > 0) {
                        if (testResult?.isSuccess == true) {
                            onConfirm(channelId, title.ifBlank { testResult!!.channelTitle }, selectedAccountId, isEncrypted, explicitBotToken.ifBlank { null })
                        } else {
                            // If not tested yet, force real test first!
                            isTesting = true
                            testResult = null
                            onTestChannel(channelId, selectedAccountId, explicitBotToken.ifBlank { null }) { res ->
                                isTesting = false
                                testResult = res
                                if (res.isSuccess) {
                                    onConfirm(channelId, title.ifBlank { res.channelTitle }, selectedAccountId, isEncrypted, explicitBotToken.ifBlank { null })
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading && !isTesting && channelId.isNotBlank() && isChannelValid,
                modifier = Modifier.testTag("confirm_add_vault_button")
            ) {
                if (isLoading || isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("جارٍ التحقق والحفظ...")
                } else {
                    Text("فحص وحفظ الخزينة ✅")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun AttachBotTokenDialog(
    accountName: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (token: String) -> Unit
) {
    var tokenInput by remember { mutableStateOf("") }
    val isValid = TdlibValidators.isValidBotToken(tokenInput)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ربط بوت تيليجرام بالحساب 🤖", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "الحساب: $accountName\nأدخل رمز Bot Token الصادر من @BotFather في تيليجرام لتمكين الرفع السحابي فائق السرعة للقنوات:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it.trim() },
                    label = { Text("Bot Token *") },
                    placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ") },
                    singleLine = true,
                    isError = tokenInput.isNotEmpty() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (tokenInput.isNotEmpty() && !isValid) {
                    Text("تنسيق الرمز غير صالح (يجب أن يكون أرقام ثم : ثم حروف وأرقام).", color = AccentRed, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tokenInput) },
                enabled = !isLoading && isValid
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                } else {
                    Text("حفظ وربط البوت ✅")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun TutorialDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دليل ربط الحسابات وجلسات TDLib 💡", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. طريقة TDLib MTProto (الهاتف أو رمز QR):", fontWeight = FontWeight.Bold, color = PrimaryCyan)
                Text("• تسجيل دخول رسمي حقيقي ببروتوكول MTProto.\n• يتم استلام رمز التحقق داخل تطبيق تيليجرام أو مسح رمز QR مباشرة من شاشة الأجهزة.\n• تشفير الجلسة محلياً بواسطة Android Keystore.", style = MaterialTheme.typography.bodySmall)

                Text("2. طريقة البوت الرسمي (BotFather):", fontWeight = FontWeight.Bold, color = SecondaryTeal)
                Text("• أنشئ بوتاً عبر @BotFather في تيليجرام.\n• انسخ Bot Token للربط المباشر.\n• أضف البوت كمسؤول (Admin) في قناتك الخاصة.", style = MaterialTheme.typography.bodySmall)

                Text("3. خاصية Session String:", fontWeight = FontWeight.Bold, color = AccentPurple)
                Text("• محرك TDLib لا يدعم استيراد Session Strings الخاصة بمكتبات غير رسمية، لذا يتم رفضها بأمان وتوجيه المستخدم لطرق المصادقة الرسمية.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("فهمت ذلك!") }
        }
    )
}
