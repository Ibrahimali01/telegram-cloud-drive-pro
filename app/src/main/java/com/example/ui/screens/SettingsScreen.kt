package com.example.ui.screens

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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.formatBytes
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryTeal
import com.example.ui.viewmodel.TeleDriveViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TeleDriveViewModel,
    onNavigateBack: () -> Unit,
    onNavigateApiCredentials: () -> Unit = {}
) {
    val context = LocalContext.current
    val savedApiId by viewModel.savedApiId.collectAsStateWithLifecycle()
    val savedApiHash by viewModel.savedApiHash.collectAsStateWithLifecycle()
    var chunkSizeMb by remember { mutableStateOf(18) }
    var autoCleanCache by remember { mutableStateOf(true) }
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }

    fun calculateCacheSize(): Long {
        val dir = File(context.cacheDir, "teledrive_streams")
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    var currentCacheBytes by remember { mutableStateOf(calculateCacheSize()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات والأمان ⚙️", fontWeight = FontWeight.Bold) },
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
            // Telegram API (my.telegram.org) Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateApiCredentials() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
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
                            Icon(Icons.Default.Code, contentDescription = null, tint = PrimaryCyan)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("بيانات Telegram API (my.telegram.org)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (savedApiId.isNotBlank()) "API ID: $savedApiId • API Hash: ${savedApiHash.take(8)}... (مشفر في عتاد الجهاز)" else "اضغط هنا لإدخال والتحقق من مفاتيح التطبيق",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onNavigateApiCredentials) {
                            Text("إدارة 🔑", color = PrimaryCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Security & Encryption Card
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AccentPurple.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = AccentPurple)
                            }
                            Column {
                                Text("محرك التشفير الصامت Zero-Knowledge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("تشفير العميل (Client-side) المستقل تماماً", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Text(
                            text = "• الخوارزمية: AES-256-GCM (Galois/Counter Mode)\n• اشتقاق المفاتيح: PBKDF2WithHmacSHA256 (10,000 Iterations)\n• طول المفتاح: 256-bit مع 128-bit Authentication Tag\n• لا يتم إرسال أي كلمات مرور إلى سيرفرات تيليجرام أو أي جهة خارجية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Chunk Engine Settings
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = PrimaryCyan)
                            }
                            Column {
                                Text("محرك تقسيم الملفات الذكي (Chunking)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("حجم القطعة الواحدة للملفات الكبيرة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 18, 30, 45).forEach { mb ->
                                FilterChip(
                                    selected = chunkSizeMb == mb,
                                    onClick = { chunkSizeMb = mb },
                                    label = { Text("$mb MB") }
                                )
                            }
                        }
                    }
                }
            }

            // Cache & Storage Cleaner
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryTeal.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = SecondaryTeal)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الذاكرة المؤقتة للبث والتحميل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("الحجم الحالي: ${formatBytes(currentCacheBytes)}", style = MaterialTheme.typography.bodySmall, color = SecondaryTeal)
                            }
                        }

                        Button(
                            onClick = {
                                val dir = File(context.cacheDir, "teledrive_streams")
                                dir.deleteRecursively()
                                currentCacheBytes = 0L
                                cacheClearedMessage = "تم تنظيف الذاكرة المؤقتة بنجاح ✅"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تنظيف الملفات المؤقتة 🧹", color = MaterialTheme.colorScheme.onSurface)
                        }

                        cacheClearedMessage?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                        }
                    }
                }
            }

            // Share & Export APK File Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null, tint = PrimaryCyan)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("مشاركة التطبيق كملف تثبيت (APK)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("إرسال ملف الـ APK الحقيقي عبر البلوتوث، تيليجرام، واتساب أو حفظه في التنزيلات", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Button(
                            onClick = {
                                com.example.util.ApkShareHelper.shareInstalledApk(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black)
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                            Text("مشاركة وتصدير ملف الـ APK 📦", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // About Project
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = PrimaryCyan)
                            Text("TeleDrive Pro Engine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "نظام سحابي مفتوح المصدر بالكامل، يستخدم شبكة Telegram الرسمية كبنية تحتية لتخزين واسترجاع الملفات مجاناً وبدون حدود سعة، مع الحفاظ على الخصوصية التامة من خلال التشفير من طرف لطرف.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
