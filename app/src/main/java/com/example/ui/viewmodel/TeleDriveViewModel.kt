package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.TeleDriveApplication
import com.example.data.db.DriveFileEntity
import com.example.data.db.DriveVaultEntity
import com.example.data.db.ProxyConfigEntity
import com.example.data.db.ShareLinkEntity
import com.example.data.db.TelegramAccountEntity
import com.example.data.repository.TeleDriveRepository
import com.example.data.model.FileCategory
import com.example.data.model.ProxyPingResult
import com.example.data.model.SortOrder
import com.example.data.model.StorageStats
import com.example.data.model.TransferProgress
import org.drinkless.tdlib.TdApi
import com.example.tdlib.TdlibValidators
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class TeleDriveViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as TeleDriveApplication).repository

    // Data streams from repository
    val accounts: StateFlow<List<TelegramAccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val primaryAccount: StateFlow<TelegramAccountEntity?> = repository.primaryAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val vaults: StateFlow<List<DriveVaultEntity>> = repository.allVaults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultVault: StateFlow<DriveVaultEntity?> = repository.defaultVault
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allFiles: StateFlow<List<DriveFileEntity>> = repository.allFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<String>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("/"))

    val proxies: StateFlow<List<ProxyConfigEntity>> = repository.allProxies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProxy: StateFlow<ProxyConfigEntity?> = repository.activeProxy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalStorageUsed: StateFlow<Long?> = repository.totalStorageUsed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalFileCount: StateFlow<Int> = repository.totalFileCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val tdlibAuthState: StateFlow<TdApi.AuthorizationState?> = repository.tdlibAuthState

    private var currentTempTdlibSessionId: Long = System.currentTimeMillis()

    // Inline errors & countdowns for TDLib auth steps
    val authInlineError = MutableStateFlow<String?>(null)
    val resendCountdown = MutableStateFlow(0)
    val floodWaitCountdown = MutableStateFlow(0)

    val savedApiId = MutableStateFlow("")
    val savedApiHash = MutableStateFlow("")
    val savedAppTitle = MutableStateFlow("TeleDrive")

    fun saveTelegramApiCredentials(apiId: String, apiHash: String, appTitle: String) {
        savedApiId.value = apiId.trim()
        savedApiHash.value = apiHash.trim()
        savedAppTitle.value = appTitle.trim()
    }

    private var resendTimerJob: Job? = null
    private var floodWaitTimerJob: Job? = null

    init {
        // Restore session for primary user account if available
        viewModelScope.launch {
            repository.restorePrimaryAccountSession()
        }

        // Observe TDLib auth state transitions to manage timeouts and steps
        viewModelScope.launch {
            tdlibAuthState.collect { state ->
                when (state) {
                    is TdApi.AuthorizationStateWaitCode -> {
                        startResendCountdown(state.codeInfo.timeout)
                    }
                    is TdApi.AuthorizationStateReady -> {
                        resendTimerJob?.cancel()
                        floodWaitTimerJob?.cancel()
                        resendCountdown.value = 0
                        floodWaitCountdown.value = 0
                        authInlineError.value = null
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startResendCountdown(timeoutSeconds: Int) {
        resendTimerJob?.cancel()
        val initial = if (timeoutSeconds > 0) timeoutSeconds else 60
        resendCountdown.value = initial
        resendTimerJob = viewModelScope.launch {
            var left = initial
            while (left > 0) {
                delay(1000L)
                left--
                resendCountdown.value = left
            }
        }
    }

    private fun handlePossibleFloodWait(message: String) {
        val seconds = TdlibValidators.extractFloodWaitSeconds(message)
        if (seconds != null && seconds > 0) {
            floodWaitTimerJob?.cancel()
            floodWaitCountdown.value = seconds
            floodWaitTimerJob = viewModelScope.launch {
                var left = seconds
                while (left > 0) {
                    delay(1000L)
                    left--
                    floodWaitCountdown.value = left
                }
            }
        }
    }

    // UI state filters
    val searchQuery = MutableStateFlow("")
    val currentFolder = MutableStateFlow("/")
    val selectedCategory = MutableStateFlow(FileCategory.ALL)
    val sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val isGridView = MutableStateFlow(true)

    // Ongoing progress & transfer status
    private val _uploadProgress = MutableStateFlow<TransferProgress?>(null)
    val uploadProgress: StateFlow<TransferProgress?> = _uploadProgress.asStateFlow()

    val diagnosticLogs: StateFlow<List<com.example.util.DiagnosticLogger.LogEntry>> = com.example.util.DiagnosticLogger.logs

    private val _channelVerificationState = MutableStateFlow<com.example.data.repository.ChannelVerificationState?>(null)
    val channelVerificationState: StateFlow<com.example.data.repository.ChannelVerificationState?> = _channelVerificationState.asStateFlow()

    fun verifyVaultAdminStatus(vaultId: Long?) {
        viewModelScope.launch {
            val vId = vaultId ?: defaultVault.value?.id ?: vaults.value.firstOrNull()?.id
            if (vId == null) {
                _channelVerificationState.value = null
                return@launch
            }
            val vault = vaults.value.find { it.id == vId }
            if (vault == null) {
                _channelVerificationState.value = null
                return@launch
            }

            _channelVerificationState.value = com.example.data.repository.ChannelVerificationState(
                channelId = vault.channelId,
                isVerifying = true,
                statusText = "جارٍ التحقق من صلاحيات البوت الإدارية عبر TDLib/Bot API..."
            )

            val res = repository.verifyChannelAdminStatusWithRetry(
                channelId = vault.channelId,
                accountId = vault.accountId,
                maxRetries = 4,
                initialDelayMs = 1000L
            ) { attempt, max, status ->
                _channelVerificationState.value = _channelVerificationState.value?.copy(
                    statusText = status,
                    attemptsDone = attempt
                )
            }

            res.onSuccess { state ->
                _channelVerificationState.value = state.copy(isVerifying = false)
            }.onFailure { err ->
                _channelVerificationState.value = com.example.data.repository.ChannelVerificationState(
                    channelId = vault.channelId,
                    isConfirmedAdmin = false,
                    canPostMessages = false,
                    isVerifying = false,
                    error = err.localizedMessage ?: "فشل التحقق من صلاحيات البوت الإدارية",
                    statusText = "فشل التحقق من رتبة المسؤول"
                )
            }
        }
    }

    fun clearDiagnosticLogs() {
        com.example.util.DiagnosticLogger.clear()
    }

    fun syncVaultFiles(vaultId: Long? = null) {
        viewModelScope.launch {
            val vId = vaultId ?: defaultVault.value?.id ?: vaults.value.firstOrNull()?.id
            if (vId == null) return@launch
            _isLoading.value = true
            val res = repository.syncVaultFilesFromTelegram(vId)
            _isLoading.value = false
            res.onSuccess { count ->
                _statusMessage.value = "تمت مزامنة القناة بنجاح! تم استعادة $count ملفاً من خوادم تيليجرام 🔄"
            }.onFailure { err ->
                _statusMessage.value = "فشلت المزامنة: ${err.localizedMessage}"
            }
        }
    }

    private val _downloadProgress = MutableStateFlow<TransferProgress?>(null)
    val downloadProgress: StateFlow<TransferProgress?> = _downloadProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _lastUploadError = MutableStateFlow<String?>(null)
    val lastUploadError: StateFlow<String?> = _lastUploadError.asStateFlow()

    fun clearUploadError() {
        _lastUploadError.value = null
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Active media for in-app video/audio/image/doc viewer
    private val _activeMedia = MutableStateFlow<Pair<DriveFileEntity, File>?>(null)
    val activeMedia: StateFlow<Pair<DriveFileEntity, File>?> = _activeMedia.asStateFlow()

    private val _pingResults = MutableStateFlow<Map<Long, ProxyPingResult>>(emptyMap())
    val pingResults: StateFlow<Map<Long, ProxyPingResult>> = _pingResults.asStateFlow()

    // Filtered & Sorted files for the current view
    val displayedFiles: StateFlow<List<DriveFileEntity>> = combine(
        allFiles,
        searchQuery,
        currentFolder,
        selectedCategory,
        sortOrder,
        accounts,
        vaults
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val files = args[0] as List<DriveFileEntity>
        val query = args[1] as String
        val folder = args[2] as String
        val category = args[3] as FileCategory
        val sort = args[4] as SortOrder
        @Suppress("UNCHECKED_CAST")
        val currentAccounts = args[5] as List<TelegramAccountEntity>
        @Suppress("UNCHECKED_CAST")
        val currentVaults = args[6] as List<DriveVaultEntity>

        if (currentAccounts.isEmpty() && currentVaults.isEmpty()) {
            return@combine emptyList()
        }

        val activeChannelIds = currentVaults.map { v: DriveVaultEntity -> TeleDriveRepository.normalizeChannelId(v.channelId) }.toSet()
        val activeAccountIds = currentAccounts.map { a: TelegramAccountEntity -> a.id }.toSet()

        var list = files.filter { file: DriveFileEntity ->
            activeAccountIds.contains(file.accountId) || activeChannelIds.contains(TeleDriveRepository.normalizeChannelId(file.channelId))
        }

        // Filter by Virtual Folder if not searching
        if (query.isBlank()) {
            if (category == FileCategory.ALL) {
                list = list.filter { it.virtualFolderPath == folder }
            }
        } else {
            val q = query.trim().lowercase()
            list = list.filter { it.originalFileName.lowercase().contains(q) || it.fileName.lowercase().contains(q) }
        }

        // Filter by Category
        list = when (category) {
            FileCategory.ALL -> list
            FileCategory.DOCUMENTS -> list.filter {
                it.mimeType.contains("pdf", true) ||
                        it.mimeType.contains("text", true) ||
                        it.mimeType.contains("word", true) ||
                        it.mimeType.contains("sheet", true) ||
                        it.mimeType.contains("presentation", true) ||
                        it.extension in listOf("doc", "docx", "pdf", "txt", "xlsx", "pptx", "csv", "json")
            }
            FileCategory.MEDIA -> list.filter {
                it.mimeType.startsWith("image/", true) || it.mimeType.startsWith("video/", true) ||
                        it.extension in listOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "mkv", "mov", "avi")
            }
            FileCategory.AUDIO -> list.filter {
                it.mimeType.startsWith("audio/", true) || it.extension in listOf("mp3", "wav", "flac", "aac", "ogg", "m4a")
            }
            FileCategory.ENCRYPTED -> list.filter { it.isEncrypted }
            FileCategory.ARCHIVES -> list.filter {
                it.extension in listOf("zip", "rar", "7z", "tar", "gz", "iso")
            }
        }

        // Apply Sorting
        when (sort) {
            SortOrder.NEWEST -> list.sortedByDescending { it.createdAt }
            SortOrder.OLDEST -> list.sortedBy { it.createdAt }
            SortOrder.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            SortOrder.SIZE_ASC -> list.sortedBy { it.sizeBytes }
            SortOrder.NAME_AZ -> list.sortedBy { it.originalFileName.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Storage Category Breakdown Stats
    val storageStats: StateFlow<StorageStats> = allFiles.combine(totalStorageUsed) { files, totalBytes ->
        StorageStats(
            totalBytes = totalBytes ?: 0L,
            totalFiles = files.size,
            imageCount = files.count { it.mimeType.startsWith("image/") },
            videoCount = files.count { it.mimeType.startsWith("video/") },
            audioCount = files.count { it.mimeType.startsWith("audio/") },
            docCount = files.count { it.mimeType.contains("pdf") || it.mimeType.contains("text") || it.extension in listOf("doc", "docx", "pdf", "txt") },
            encryptedCount = files.count { it.isEncrypted },
            archiveCount = files.count { it.extension in listOf("zip", "rar", "7z") }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StorageStats())

    // -------------------------------------------------------------
    // REAL TDLIB AUTHENTICATION & MULTI-ACCOUNT ACTIONS
    // -------------------------------------------------------------

    fun startTdlibPhoneAuth(
        apiIdStr: String,
        apiHash: String,
        phone: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            authInlineError.value = null

            // Validate API ID (Req 1)
            if (!TdlibValidators.isValidApiId(apiIdStr)) {
                authInlineError.value = "يجب أن يتكون API ID من أرقام فقط وأن يكون عدداً موجباً"
                return@launch
            }

            // Validate API Hash (Req 1)
            if (!TdlibValidators.isValidApiHash(apiHash)) {
                authInlineError.value = "يجب أن يتكون API Hash من 32 خانة ست عشرية (Hex) بالضبط"
                return@launch
            }

            // Validate Phone (Req 2)
            val cleanPhone = TdlibValidators.normalizePhone(phone)
            if (!TdlibValidators.isValidPhoneE164(cleanPhone)) {
                authInlineError.value = "رقم الهاتف يجب أن يطابق المعيار الدولي E.164 (مثال: +966501234567)"
                return@launch
            }

            _isLoading.value = true
            currentTempTdlibSessionId = System.currentTimeMillis()
            val apiId = apiIdStr.trim().toInt()
            val result = repository.startTdlibAuth(currentTempTdlibSessionId, apiId, apiHash.trim(), cleanPhone)
            _isLoading.value = false

            result.onSuccess {
                authInlineError.value = null
                onSuccess()
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "فشل بدء جلسة TDLib"
                authInlineError.value = msg
                handlePossibleFloodWait(msg)
            }
        }
    }

    fun submitTdlibCode(
        code: String,
        apiIdStr: String,
        apiHash: String,
        label: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            authInlineError.value = null
            val cleanCode = code.trim()
            if (cleanCode.isEmpty() || !cleanCode.all { it.isDigit() }) {
                authInlineError.value = "رمز التحقق يجب أن يحتوي على أرقام فقط"
                return@launch
            }

            _isLoading.value = true
            val result = repository.submitTdlibCode(currentTempTdlibSessionId, cleanCode)
            _isLoading.value = false

            result.onSuccess {
                authInlineError.value = null
                // If TDLib entered ready immediately (no 2FA)
                if (repository.tdlibAuthState.value is TdApi.AuthorizationStateReady) {
                    finalizeAccount(apiIdStr, apiHash, "", label, onSuccess)
                }
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "رمز التحقق غير صالح"
                authInlineError.value = msg
                handlePossibleFloodWait(msg)
            }
        }
    }

    fun submitTdlib2FaPassword(
        password: String,
        apiIdStr: String,
        apiHash: String,
        label: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            authInlineError.value = null
            if (password.isEmpty()) {
                authInlineError.value = "يرجى إدخال كلمة المرور بخطوتين (2FA)"
                return@launch
            }

            _isLoading.value = true
            val result = repository.submitTdlib2FaPassword(currentTempTdlibSessionId, password)
            _isLoading.value = false

            result.onSuccess {
                authInlineError.value = null
                finalizeAccount(apiIdStr, apiHash, "", label, onSuccess)
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "كلمة المرور غير صحيحة"
                authInlineError.value = msg
                handlePossibleFloodWait(msg)
            }
        }
    }

    fun startTdlibBotAuth(
        apiIdStr: String,
        apiHash: String,
        botToken: String,
        label: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            authInlineError.value = null

            if (!TdlibValidators.isValidApiId(apiIdStr)) {
                authInlineError.value = "يجب أن يتكون API ID من أرقام فقط وأن يكون عدداً موجباً"
                return@launch
            }
            if (!TdlibValidators.isValidApiHash(apiHash)) {
                authInlineError.value = "يجب أن يتكون API Hash من 32 خانة ست عشرية (Hex) بالضبط"
                return@launch
            }
            if (!TdlibValidators.isValidBotToken(botToken)) {
                authInlineError.value = "تنسيق رمز البوت غير صالح. يجب أن يكون 123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ"
                return@launch
            }

            _isLoading.value = true
            currentTempTdlibSessionId = System.currentTimeMillis()
            val apiId = apiIdStr.trim().toInt()
            val result = repository.startTdlibBotAuth(currentTempTdlibSessionId, apiId, apiHash.trim(), botToken.trim())
            _isLoading.value = false

            result.onSuccess {
                authInlineError.value = null
                finalizeAccount(apiIdStr, apiHash, botToken.trim(), label, onSuccess)
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "فشل ربط البوت عبر TDLib"
                authInlineError.value = msg
            }
        }
    }

    fun startTdlibQrAuth(
        apiIdStr: String,
        apiHash: String
    ) {
        viewModelScope.launch {
            authInlineError.value = null

            if (!TdlibValidators.isValidApiId(apiIdStr)) {
                authInlineError.value = "يجب أن يتكون API ID من أرقام فقط وأن يكون عدداً موجباً"
                return@launch
            }
            if (!TdlibValidators.isValidApiHash(apiHash)) {
                authInlineError.value = "يجب أن يتكون API Hash من 32 خانة ست عشرية (Hex) بالضبط"
                return@launch
            }

            _isLoading.value = true
            currentTempTdlibSessionId = System.currentTimeMillis()
            val apiId = apiIdStr.trim().toInt()
            val result = repository.startTdlibQrAuth(currentTempTdlibSessionId, apiId, apiHash.trim())
            _isLoading.value = false

            result.onFailure { err ->
                authInlineError.value = err.localizedMessage ?: "فشل بدء جلسة QR عبر TDLib"
            }
        }
    }

    fun resendTdlibCode() {
        viewModelScope.launch {
            if (resendCountdown.value > 0) return@launch
            _isLoading.value = true
            authInlineError.value = null
            val result = repository.resendTdlibCode(currentTempTdlibSessionId)
            _isLoading.value = false
            result.onFailure { err ->
                val msg = err.localizedMessage ?: "فشل إعادة إرسال رمز التحقق"
                authInlineError.value = msg
                handlePossibleFloodWait(msg)
            }
        }
    }

    fun cancelTdlibLogin() {
        viewModelScope.launch {
            repository.cancelTdlibAuth(currentTempTdlibSessionId)
            authInlineError.value = null
            resendCountdown.value = 0
            floodWaitCountdown.value = 0
            resendTimerJob?.cancel()
            floodWaitTimerJob?.cancel()
        }
    }

    fun finalizeAccount(
        apiIdStr: String,
        apiHash: String,
        botToken: String,
        label: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.finalizeTdlibAccount(
                tempSessionId = currentTempTdlibSessionId,
                apiId = apiIdStr,
                apiHash = apiHash,
                botToken = botToken,
                accountLabel = label
            )
            _isLoading.value = false
            result.onSuccess {
                _statusMessage.value = "تم توثيق وربط الحساب بنجاح عبر TDLib 👤✅"
                authInlineError.value = null
                onSuccess()
            }.onFailure { err ->
                authInlineError.value = err.localizedMessage ?: "فشل حفظ الحساب"
            }
        }
    }

    fun clearAuthInlineError() {
        authInlineError.value = null
    }

    fun setPrimaryAccount(accountId: Long) {
        viewModelScope.launch {
            repository.setPrimaryAccount(accountId)
            _statusMessage.value = "تم تعيين الحساب كحساب رئيسي 🌟"
        }
    }

    fun deleteAccount(account: TelegramAccountEntity) {
        viewModelScope.launch {
            repository.deleteAccountAndSession(account)
            _statusMessage.value = "تم حذف الحساب وإنهاء الجلسة 🚪"
        }
    }

    fun addBotAccountDirect(
        botToken: String,
        label: String = "",
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.addAccount(accountName = label, botToken = botToken)
            _isLoading.value = false
            result.onSuccess {
                _statusMessage.value = "تم ربط بوت تيليجرام بنجاح 🤖✅"
                authInlineError.value = null
                onSuccess()
            }.onFailure { err ->
                val msg = "فشل ربط البوت: ${err.localizedMessage ?: "تحقق من صحة الرمز"}"
                authInlineError.value = msg
                _statusMessage.value = msg
            }
        }
    }

    fun updateAccountBotToken(
        accountId: Long,
        botToken: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.updateAccountBotToken(accountId, botToken)
            _isLoading.value = false
            result.onSuccess {
                _statusMessage.value = "تم تحديث وربط رمز البوت بالحساب 🤖✅"
                onSuccess()
            }.onFailure { err ->
                _statusMessage.value = "فشل تحديث رمز البوت: ${err.localizedMessage}"
            }
        }
    }

    fun testChannelConnection(
        accountId: Long,
        channelId: String,
        explicitBotToken: String? = null,
        onResult: (com.example.data.repository.ChannelTestResult) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.testChannelConnection(accountId, channelId, explicitBotToken)
            _isLoading.value = false
            onResult(res)
        }
    }

    fun addVault(
        channelId: String,
        title: String,
        accountId: Long,
        isEncrypted: Boolean,
        explicitBotToken: String? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.verifyAndAddVault(channelId, title, accountId, isEncrypted, explicitBotToken = explicitBotToken)
            _isLoading.value = false
            result.onSuccess {
                _statusMessage.value = "تم ربط قناة التخزين بنجاح 📁"
                onSuccess()
            }.onFailure { err ->
                _statusMessage.value = "فشل التحقق من القناة: ${err.localizedMessage}"
            }
        }
    }

    fun setDefaultVault(vaultId: Long) {
        viewModelScope.launch {
            repository.setDefaultVault(vaultId)
            _statusMessage.value = "تم تحديد الخزينة الافتراضية للرفع"
        }
    }

    fun deleteVault(vault: DriveVaultEntity) {
        viewModelScope.launch {
            repository.deleteVault(vault)
            _statusMessage.value = "تم حذف القناة من التطبيق"
        }
    }

    fun uploadFile(
        fileUri: Uri,
        virtualFolder: String,
        isEncrypted: Boolean,
        encryptionPassword: String,
        targetVaultId: Long?,
        onError: ((String) -> Unit)? = null,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _lastUploadError.value = null
            val result = repository.uploadFile(
                fileUri = fileUri,
                virtualFolder = virtualFolder,
                isEncrypted = isEncrypted,
                encryptionPassword = encryptionPassword,
                targetVaultId = targetVaultId
            ) { progress ->
                _uploadProgress.value = progress
            }
            _isLoading.value = false
            _uploadProgress.value = null

            result.onSuccess {
                _statusMessage.value = "تم رفع الملف بنجاح إلى تيليجرام ☁️"
                onComplete()
            }.onFailure { err ->
                val errorMsg = err.localizedMessage ?: "فشل الرفع إلى تيليجرام"
                _lastUploadError.value = errorMsg
                _statusMessage.value = "فشل الرفع: $errorMsg"
                onError?.invoke(errorMsg)
            }
        }
    }

    fun downloadAndOpenMedia(
        file: DriveFileEntity,
        decryptionPassword: String = ""
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.downloadOrStreamFile(
                file = file,
                decryptionPassword = decryptionPassword
            ) { progress ->
                _downloadProgress.value = progress
            }
            _isLoading.value = false
            _downloadProgress.value = null

            result.onSuccess { localFile ->
                _activeMedia.value = Pair(file, localFile)
            }.onFailure { err ->
                _statusMessage.value = "فشل التحميل/فك التشفير: ${err.localizedMessage}"
            }
        }
    }

    fun closeMediaViewer() {
        _activeMedia.value = null
    }

    fun deleteFile(file: DriveFileEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.deleteFile(file)
            _isLoading.value = false
            result.onSuccess {
                _statusMessage.value = "تم حذف الملف من تيليجرام والخزينة 🗑️"
            }.onFailure { err ->
                _statusMessage.value = "فشل الحذف: ${err.localizedMessage}"
            }
        }
    }

    fun renameFile(file: DriveFileEntity, newName: String) {
        viewModelScope.launch {
            repository.renameFile(file, newName)
            _statusMessage.value = "تم تغيير اسم الملف بنجاح"
        }
    }

    fun moveFile(file: DriveFileEntity, newFolder: String) {
        viewModelScope.launch {
            repository.moveFileToFolder(file, newFolder)
            _statusMessage.value = "تم نقل الملف إلى $newFolder"
        }
    }

    fun addProxy(
        title: String,
        type: String,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repository.addProxy(title, type, host, port, username, password)
            _statusMessage.value = "تمت إضافة البروكسي بنجاح 🛡️"
            onSuccess()
        }
    }

    fun toggleProxy(proxyId: Long, active: Boolean) {
        viewModelScope.launch {
            repository.toggleProxyActive(proxyId, active)
            _statusMessage.value = if (active) "تم تفعيل البروكسي ⚡" else "تم تعطيل البروكسي (اتصال مباشر)"
        }
    }

    fun testProxy(proxy: ProxyConfigEntity) {
        viewModelScope.launch {
            val res = repository.testProxyLatency(proxy)
            _pingResults.value = _pingResults.value.toMutableMap().apply { put(proxy.id, res) }
            if (res.success) {
                _statusMessage.value = "بينغ البروكسي (${proxy.title}): ${res.latencyMs}ms ✅"
            } else {
                _statusMessage.value = "فشل الاتصال بالبروكسي: ${res.errorMessage}"
            }
        }
    }

    fun deleteProxy(proxy: ProxyConfigEntity) {
        viewModelScope.launch {
            repository.deleteProxy(proxy)
            _statusMessage.value = "تم حذف البروكسي"
        }
    }

    fun createShareLink(
        fileId: Long,
        password: String?,
        expiresInHours: Int?,
        onCreated: (ShareLinkEntity) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.createShareLink(fileId, password, expiresInHours)
            result.onSuccess { link ->
                _statusMessage.value = "تم إنشاء رابط مشاركة آمن 🔗"
                onCreated(link)
            }.onFailure { err ->
                _statusMessage.value = "فشل إنشاء الرابط: ${err.localizedMessage}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun createFolder(folderName: String) {
        val cleanName = folderName.trim().replace("/", "")
        if (cleanName.isNotBlank()) {
            val newPath = if (currentFolder.value == "/") "/$cleanName" else "${currentFolder.value}/$cleanName"
            currentFolder.value = newPath
            _statusMessage.value = "تم إنشاء المجلد: $newPath"
        }
    }

    fun navigateUpFolder() {
        val curr = currentFolder.value
        if (curr == "/") return
        val parent = curr.substringBeforeLast('/', "")
        currentFolder.value = if (parent.isBlank()) "/" else parent
    }
}
