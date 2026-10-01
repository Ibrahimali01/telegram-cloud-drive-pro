package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.ProxyConfigEntity
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentRed
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryTeal
import com.example.ui.viewmodel.TeleDriveViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(
    viewModel: TeleDriveViewModel,
    onNavigateBack: () -> Unit
) {
    val proxies by viewModel.proxies.collectAsStateWithLifecycle()
    val pingResults by viewModel.pingResults.collectAsStateWithLifecycle()

    var showAddProxyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات البروكسي والشبكة 🛡️", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddProxyDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Proxy", tint = PrimaryCyan)
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
            // Description Banner
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SecondaryTeal.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = SecondaryTeal)
                        Text(
                            text = "يدعم التطبيق بروتوكولات SOCKS5 و HTTP و MTProto لتسريع نقل البيانات وتخطي القيود الجغرافية وشبكات VPN البطيئة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Direct Connection Option Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (proxies.none { it.isActive }) PrimaryCyan.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (proxies.none { it.isActive }) PrimaryCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = PrimaryCyan)
                            }
                            Column {
                                Text("اتصال مباشر (Direct CDN)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("استخدام سيرفرات تيليجرام الرسمية مباشرة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Switch(
                            checked = proxies.none { it.isActive },
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    proxies.filter { it.isActive }.forEach { viewModel.toggleProxy(it.id, false) }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryCyan)
                        )
                    }
                }
            }

            // Custom Proxies List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("خوادم البروكسي المخصصة (${proxies.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            if (proxies.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("لم تقم بإضافة بروكسيات مخصصة بعد", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = { showAddProxyDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                            ) {
                                Text("+ إضافة بروكسي SOCKS5", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(proxies, key = { "proxy_${it.id}" }) { proxy ->
                    val ping = pingResults[proxy.id]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (proxy.isActive) AccentGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
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
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(proxy.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SecondaryTeal.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(proxy.type, style = MaterialTheme.typography.labelSmall, color = SecondaryTeal, fontSize = 10.sp)
                                    }
                                }

                                Text("${proxy.host}:${proxy.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = { viewModel.testProxy(proxy) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Speed, contentDescription = "Test Ping", modifier = Modifier.size(16.dp), tint = PrimaryCyan)
                                    }

                                    if (ping != null) {
                                        if (ping.success) {
                                            val pingColor = when {
                                                ping.latencyMs < 200 -> AccentGreen
                                                ping.latencyMs < 500 -> AccentAmber
                                                else -> AccentRed
                                            }
                                            Text("${ping.latencyMs} ms", style = MaterialTheme.typography.labelSmall, color = pingColor, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text("غير متاح ❌", style = MaterialTheme.typography.labelSmall, color = AccentRed)
                                        }
                                    } else if (proxy.lastPingMs > 0) {
                                        Text("${proxy.lastPingMs} ms", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Switch(
                                    checked = proxy.isActive,
                                    onCheckedChange = { viewModel.toggleProxy(proxy.id, it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = AccentGreen)
                                )

                                IconButton(onClick = { viewModel.deleteProxy(proxy) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Proxy Dialog
    if (showAddProxyDialog) {
        AddProxyDialog(
            onDismiss = { showAddProxyDialog = false },
            onConfirm = { title, type, host, port, user, pwd ->
                viewModel.addProxy(title, type, host, port, user, pwd) {
                    showAddProxyDialog = false
                }
            }
        )
    }
}

@Composable
private fun AddProxyDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, type: String, host: String, port: Int, username: String?, password: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("SOCKS5") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة خادم بروكسي 🛡️", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SOCKS5", "HTTP", "MTPROTO").forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم الخادم") },
                    placeholder = { Text("خادم ألمانيا السريع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host / IP *") },
                    placeholder = { Text("192.168.1.1 أو proxy.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("proxy_host_input")
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port *") },
                    placeholder = { Text("1080 أو 8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("proxy_port_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("اسم المستخدم (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("كلمة المرور (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = port.toIntOrNull() ?: 1080
                    if (host.isNotBlank()) {
                        onConfirm(title.ifBlank { "Proxy $host" }, type, host, p, username, password)
                    }
                },
                enabled = host.isNotBlank() && port.toIntOrNull() != null,
                modifier = Modifier.testTag("confirm_add_proxy_button")
            ) {
                Text("إضافة وحفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
