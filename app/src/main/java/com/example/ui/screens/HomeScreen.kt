package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.DriveFileEntity
import com.example.data.model.FileCategory
import com.example.data.model.SortOrder
import com.example.data.model.formatBytes
import com.example.ui.components.FileTypeIcon
import com.example.ui.components.MoveFileDialog
import com.example.ui.components.PasswordPromptDialog
import com.example.ui.components.RenameFileDialog
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentRed
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.TelegramBlue
import com.example.ui.viewmodel.TeleDriveViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: TeleDriveViewModel,
    onNavigateUpload: () -> Unit,
    onNavigateAccounts: () -> Unit,
    onNavigateProxy: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val displayedFiles by viewModel.displayedFiles.collectAsStateWithLifecycle()
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val totalStorageUsed by viewModel.totalStorageUsed.collectAsStateWithLifecycle()
    val totalFileCount by viewModel.totalFileCount.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val primaryAccount by viewModel.primaryAccount.collectAsStateWithLifecycle()
    val defaultVault by viewModel.defaultVault.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val vaults by viewModel.vaults.collectAsStateWithLifecycle()
    val activeProxy by viewModel.activeProxy.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val availableFolders by viewModel.folders.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showFabMenu by remember { mutableStateOf(false) }
    var fileForPasswordPrompt by remember { mutableStateOf<DriveFileEntity?>(null) }
    var fileForRename by remember { mutableStateOf<DriveFileEntity?>(null) }
    var fileForMove by remember { mutableStateOf<DriveFileEntity?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showShareModalForFile by remember { mutableStateOf<DriveFileEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(visible = showFabMenu) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                com.example.util.ApkShareHelper.shareInstalledApk(context)
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = PrimaryCyan,
                            modifier = Modifier.testTag("fab_share_apk")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("مشاركة ملف الـ APK 📦", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        SmallFloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                showNewFolderDialog = true
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = PrimaryCyan,
                            modifier = Modifier.testTag("fab_new_folder")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("مجلد جديد", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        SmallFloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                onNavigateUpload()
                            },
                            containerColor = AccentPurple,
                            contentColor = Color.White,
                            modifier = Modifier.testTag("fab_upload_encrypted")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.EnhancedEncryption, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("رفع مشفر E2EE", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = onNavigateUpload,
                    containerColor = PrimaryCyan,
                    contentColor = Color.Black,
                    modifier = Modifier.testTag("main_fab_upload")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = "رفع ملف", modifier = Modifier.size(30.dp))
                }
            }
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
            // Header: App branding & Connection Pills
            item {
                HeaderSection(
                    primaryAccount = primaryAccount?.accountName ?: accounts.firstOrNull()?.accountName,
                    vaultTitle = defaultVault?.channelTitle ?: vaults.firstOrNull()?.channelTitle,
                    activeProxy = activeProxy?.title,
                    onNavigateAccounts = onNavigateAccounts,
                    onNavigateProxy = onNavigateProxy,
                    onNavigateSettings = onNavigateSettings,
                    onNavigateUpload = onNavigateUpload,
                    onSyncChannel = { viewModel.syncVaultFiles() }
                )
            }

            // Storage Overview Dashboard Card
            item {
                StorageDashboardCard(
                    totalBytes = totalStorageUsed ?: 0L,
                    totalFiles = totalFileCount,
                    storageStats = storageStats
                )
            }

            // Quick APK Share Card (Sends real .apk file, not link/text)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(PrimaryCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(24.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("مشاركة ملف التطبيق (APK) 📦", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("إرسال ملف الـ APK الحقيقي مباشرة لتثبيته على أي هاتف أندرويد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Button(
                            onClick = {
                                com.example.util.ApkShareHelper.shareInstalledApk(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("إرسال APK", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Search Bar & View/Sort Switchers
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_files_input"),
                        placeholder = { Text("بحث في السحابة...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryCyan) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    IconButton(
                        onClick = { viewModel.isGridView.value = !isGridView },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("toggle_grid_view_button")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List",
                            tint = PrimaryCyan
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("sort_menu_button")
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Sort", tint = SecondaryTeal)
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOrder.values().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.titleAr) },
                                    onClick = {
                                        viewModel.sortOrder.value = order
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Category Filter Row
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(FileCategory.values()) { category ->
                        val isSelected = selectedCategory == category
                        val count = when (category) {
                            FileCategory.ALL -> totalFileCount
                            FileCategory.DOCUMENTS -> storageStats.docCount
                            FileCategory.MEDIA -> storageStats.imageCount + storageStats.videoCount
                            FileCategory.AUDIO -> storageStats.audioCount
                            FileCategory.ENCRYPTED -> storageStats.encryptedCount
                            FileCategory.ARCHIVES -> storageStats.archiveCount
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) PrimaryCyan else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .border(
                                    1.dp,
                                    if (isSelected) PrimaryCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { viewModel.selectedCategory.value = category }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = category.titleAr,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.Black.copy(alpha = 0.2f) else PrimaryCyan.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else PrimaryCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Folder Breadcrumb Navigator
            if (searchQuery.isBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (currentFolder != "/") {
                                IconButton(
                                    onClick = { viewModel.navigateUpFolder() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp), tint = PrimaryCyan)
                                }
                            }
                            Icon(Icons.Default.Folder, contentDescription = null, tint = SecondaryTeal, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (currentFolder == "/") "السحابة الرئيسية (/)" else currentFolder,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        TextButton(
                            onClick = { showNewFolderDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("+ مجلد", style = MaterialTheme.typography.labelSmall, color = PrimaryCyan)
                        }
                    }
                }
            }

            // File Items Section
            if (displayedFiles.isEmpty()) {
                item {
                    EmptyStateCard(
                        hasAccount = primaryAccount != null || accounts.isNotEmpty() || defaultVault != null || vaults.isNotEmpty(),
                        onConnectTelegram = onNavigateAccounts,
                        onUploadFile = onNavigateUpload
                    )
                }
            } else {
                if (isGridView) {
                    item {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            maxItemsInEachRow = 2,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            displayedFiles.forEach { file ->
                                FileGridCard(
                                    file = file,
                                    modifier = Modifier.fillMaxWidth(0.48f),
                                    onClick = {
                                        if (file.isEncrypted) {
                                            fileForPasswordPrompt = file
                                        } else {
                                            viewModel.downloadAndOpenMedia(file)
                                        }
                                    },
                                    onRename = { fileForRename = file },
                                    onMove = { fileForMove = file },
                                    onShare = { showShareModalForFile = file },
                                    onDelete = { viewModel.deleteFile(file) }
                                )
                            }
                        }
                    }
                } else {
                    items(displayedFiles, key = { "file_${it.id}" }) { file ->
                        FileListCard(
                            file = file,
                            onClick = {
                                if (file.isEncrypted) {
                                    fileForPasswordPrompt = file
                                } else {
                                    viewModel.downloadAndOpenMedia(file)
                                }
                            },
                            onRename = { fileForRename = file },
                            onMove = { fileForMove = file },
                            onShare = { showShareModalForFile = file },
                            onDelete = { viewModel.deleteFile(file) }
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    fileForPasswordPrompt?.let { file ->
        PasswordPromptDialog(
            title = "فك تشفير ${file.originalFileName} 🔒",
            onConfirm = { pwd ->
                fileForPasswordPrompt = null
                viewModel.downloadAndOpenMedia(file, pwd)
            },
            onDismiss = { fileForPasswordPrompt = null }
        )
    }

    fileForRename?.let { file ->
        RenameFileDialog(
            currentName = file.originalFileName,
            onConfirm = { newName ->
                viewModel.renameFile(file, newName)
                fileForRename = null
            },
            onDismiss = { fileForRename = null }
        )
    }

    fileForMove?.let { file ->
        MoveFileDialog(
            availableFolders = availableFolders,
            currentFolder = file.virtualFolderPath,
            onConfirm = { targetPath ->
                viewModel.moveFile(file, targetPath)
                fileForMove = null
            },
            onDismiss = { fileForMove = null }
        )
    }

    if (showNewFolderDialog) {
        var folderInput by remember { mutableStateOf("") }
        com.example.ui.components.RenameFileDialog(
            currentName = "",
            onConfirm = { name ->
                viewModel.createFolder(name)
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    showShareModalForFile?.let { file ->
        ShareFileDialog(
            file = file,
            onDismiss = { showShareModalForFile = null },
            onCreateLink = { pwd, hours ->
                viewModel.createShareLink(file.id, pwd, hours) {
                    showShareModalForFile = null
                }
            }
        )
    }
}

@Composable
private fun HeaderSection(
    primaryAccount: String?,
    vaultTitle: String?,
    activeProxy: String?,
    onNavigateAccounts: () -> Unit,
    onNavigateProxy: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateUpload: () -> Unit,
    onSyncChannel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (primaryAccount != null) AccentGreen else AccentRed)
                )
                Text(
                    text = "TeleDrive Pro",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = PrimaryCyan
                )
            }
            Text(
                text = if (vaultTitle != null) "الخزينة: $vaultTitle" else "سحابة تيليجرام غير المحدودة",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick Sync Channel Button
            IconButton(
                onClick = onSyncChannel,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SecondaryTeal)
                    .testTag("header_sync_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "مزامنة القناة", tint = Color.Black, modifier = Modifier.size(20.dp))
            }

            // Quick Upload Button in Header
            IconButton(
                onClick = onNavigateUpload,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryCyan)
                    .testTag("header_upload_button")
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = "رفع ملف", tint = Color.Black, modifier = Modifier.size(20.dp))
            }

            // Proxy Status Chip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (activeProxy != null) AccentGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        if (activeProxy != null) AccentGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onNavigateProxy() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = "Proxy",
                        tint = if (activeProxy != null) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = activeProxy ?: "مباشر",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (activeProxy != null) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Share APK Button
            IconButton(
                onClick = {
                    com.example.util.ApkShareHelper.shareInstalledApk(context)
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryCyan.copy(alpha = 0.15f))
                    .testTag("header_share_apk_button")
            ) {
                Icon(Icons.Default.InstallMobile, contentDescription = "Share APK File", tint = PrimaryCyan, modifier = Modifier.size(19.dp))
            }

            // Account Button
            IconButton(
                onClick = onNavigateAccounts,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("header_accounts_button")
            ) {
                Icon(Icons.Default.Storage, contentDescription = "Accounts", tint = PrimaryCyan, modifier = Modifier.size(19.dp))
            }

            // Settings Button
            IconButton(
                onClick = onNavigateSettings,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("header_settings_button")
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Security Vault Settings", tint = SecondaryTeal, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun StorageDashboardCard(
    totalBytes: Long,
    totalFiles: Int,
    storageStats: com.example.data.model.StorageStats
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "المساحة السحابية المستخدمة",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatBytes(totalBytes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = PrimaryCyan
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF0072FF))))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "غير محدود ∞",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Visual Stats Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatPill("الملفات", "$totalFiles", PrimaryCyan)
                StatPill("الوسائط", "${storageStats.imageCount + storageStats.videoCount}", SecondaryTeal)
                StatPill("مشفر E2EE", "${storageStats.encryptedCount}", AccentPurple)
                StatPill("المستندات", "${storageStats.docCount}", AccentGreen)
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCard(
    file: DriveFileEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                FileTypeIcon(file = file, iconSize = 24)

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", modifier = Modifier.size(16.dp))
                    }

                    FileDropdownMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onRename = onRename,
                        onMove = onMove,
                        onShare = onShare,
                        onDelete = onDelete
                    )
                }
            }

            Text(
                text = file.originalFileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatBytes(file.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (file.isEncrypted) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        tint = AccentPurple,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListCard(
    file: DriveFileEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FileTypeIcon(file = file, iconSize = 24)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = file.originalFileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatBytes(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = dateFormatter.format(Date(file.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (file.isChunked) {
                        Text(
                            text = "${file.totalChunks} أجزاء",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryTeal
                        )
                    }
                }
            }

            if (file.isEncrypted) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Encrypted",
                    tint = AccentPurple,
                    modifier = Modifier.size(16.dp)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }

                FileDropdownMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onRename = onRename,
                    onMove = onMove,
                    onShare = onShare,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun FileDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("مشاركة رابط آمن 🔗") },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryCyan) },
            onClick = {
                onDismiss()
                onShare()
            }
        )
        DropdownMenuItem(
            text = { Text("إعادة تسمية ✏️") },
            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
            onClick = {
                onDismiss()
                onRename()
            }
        )
        DropdownMenuItem(
            text = { Text("نقل إلى مجلد 📂") },
            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
            onClick = {
                onDismiss()
                onMove()
            }
        )
        DropdownMenuItem(
            text = { Text("حذف نهائي 🗑️", color = AccentRed) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed) },
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}

@Composable
private fun EmptyStateCard(
    hasAccount: Boolean,
    onConnectTelegram: () -> Unit,
    onUploadFile: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = null,
                tint = PrimaryCyan,
                modifier = Modifier.size(54.dp)
            )

            Text(
                text = if (hasAccount) "سحابتك وقناتك جاهزة للرفع 🚀" else "اربط حساب تيليجرام أولاً 🔑",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (hasAccount)
                    "تم ربط قناتك بنجاح! اضغط على زر الرفع أدناه لاختيار ملفاتك (صور، فيديو، مستندات) ورفعها مباشرة إلى قناتك السحابية."
                else
                    "قم بتسجيل الدخول برقم هاتفك أو البوت وإضافة قناة تخزين للبدء في استخدام تيليجرام كمساحة تخزين سحابية غير محدودة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            androidx.compose.material3.Button(
                onClick = if (hasAccount) onUploadFile else onConnectTelegram,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                modifier = Modifier.testTag("empty_state_action_button")
            ) {
                Icon(if (hasAccount) Icons.Default.UploadFile else Icons.Default.Storage, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (hasAccount) "رفع ملف الآن 📤" else "ربط حساب تيليجرام 🔗",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ShareFileDialog(
    file: DriveFileEntity,
    onDismiss: () -> Unit,
    onCreateLink: (password: String?, expiresInHours: Int?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var expirationHours by remember { mutableStateOf<Int?>(24) }
    var usePassword by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء رابط مشاركة آمن 🔗", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "الملف: ${file.originalFileName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حماية بكلمة مرور:")
                    androidx.compose.material3.Switch(
                        checked = usePassword,
                        onCheckedChange = { usePassword = it }
                    )
                }

                if (usePassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("كلمة المرور للرابط") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("صلاحية الرابط:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "ساعة", 24 to "يوم", 168 to "أسبوع", null to "دائم").forEach { (hrs, label) ->
                        val isSel = expirationHours == hrs
                        androidx.compose.material3.FilterChip(
                            selected = isSel,
                            onClick = { expirationHours = hrs },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    onCreateLink(if (usePassword && password.isNotBlank()) password else null, expirationHours)
                }
            ) {
                Text("توليد الرابط")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
