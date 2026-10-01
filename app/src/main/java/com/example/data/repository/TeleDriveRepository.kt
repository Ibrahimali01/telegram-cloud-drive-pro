package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.crypto.CryptoEngine
import com.example.data.db.AppDatabase
import com.example.data.db.DriveFileEntity
import com.example.data.db.DriveVaultEntity
import com.example.data.db.FileChunkEntity
import com.example.data.db.ProxyConfigEntity
import com.example.data.db.ShareLinkEntity
import com.example.data.db.TelegramAccountEntity
import com.example.data.model.ProxyPingResult
import com.example.data.model.TransferProgress
import com.example.network.ProgressRequestBody
import com.example.network.TelegramClientProvider
import com.example.network.TelegramUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.drinkless.tdlib.TdApi
import com.example.crypto.SecureCredentialsStorage
import com.example.tdlib.TdlibClient
import com.example.tdlib.TdlibSessionManager
import com.example.tdlib.TdlibValidators
import com.example.util.DiagnosticLogger
import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

data class ChannelVerificationState(
    val channelId: String = "",
    val isConfirmedAdmin: Boolean = false,
    val canPostMessages: Boolean = false,
    val statusText: String = "",
    val isVerifying: Boolean = false,
    val error: String? = null,
    val attemptsDone: Int = 0
)

data class ChannelTestResult(
    val isSuccess: Boolean,
    val channelTitle: String = "",
    val normalizedChannelId: String = "",
    val errorMessage: String = ""
)

class TeleDriveRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    companion object {
        fun normalizeChannelId(channelId: String): String {
            val trimmed = channelId.trim()
            if (trimmed.isEmpty()) return ""
            val cleaned = trimmed
                .removePrefix("https://t.me/")
                .removePrefix("http://t.me/")
                .removePrefix("t.me/")
                .trim()
            return when {
                cleaned.startsWith("-100") -> cleaned
                cleaned.startsWith("@") -> cleaned
                cleaned.toLongOrNull() != null -> {
                    if (!cleaned.startsWith("-")) "-100$cleaned" else cleaned
                }
                else -> "@$cleaned"
            }
        }
    }
    private val accountDao = database.accountDao()
    private val vaultDao = database.vaultDao()
    private val fileDao = database.fileDao()
    private val chunkDao = database.chunkDao()
    private val proxyDao = database.proxyDao()
    private val shareLinkDao = database.shareLinkDao()

    val tdlibSessionManager = TdlibSessionManager(context)

    // Observe Flows
    val allAccounts: Flow<List<TelegramAccountEntity>> = accountDao.getAllAccounts()
    val primaryAccount: Flow<TelegramAccountEntity?> = accountDao.getPrimaryAccount()
    val allVaults: Flow<List<DriveVaultEntity>> = vaultDao.getAllVaults()
    val defaultVault: Flow<DriveVaultEntity?> = vaultDao.getDefaultVault()
    val allFiles: Flow<List<DriveFileEntity>> = fileDao.getAllFiles()
    val allFolders: Flow<List<String>> = fileDao.getAllFolders()
    val allProxies: Flow<List<ProxyConfigEntity>> = proxyDao.getAllProxies()
    val activeProxy: Flow<ProxyConfigEntity?> = proxyDao.getActiveProxy()
    val totalStorageUsed: Flow<Long?> = fileDao.getTotalStorageUsed()
    val totalFileCount: Flow<Int> = fileDao.getTotalFileCount()

    // TDLib active auth state flow
    val tdlibAuthState: StateFlow<TdApi.AuthorizationState?> = tdlibSessionManager.activeClientAuthState

    // Chunk size limit: 18 MB for robust Telegram uploads
    private val CHUNK_SIZE_BYTES = 18 * 1024 * 1024L

    // -------------------------------------------------------------
    // TELEGRAM ACCOUNT MANAGEMENT & REAL TDLIB AUTHENTICATION
    // -------------------------------------------------------------

    suspend fun startTdlibAuth(
        tempSessionId: Long,
        apiId: Int,
        apiHash: String,
        phoneNumber: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val clientRes = tdlibSessionManager.startNewAuthentication(tempSessionId, apiId, apiHash)
        if (clientRes.isFailure) {
            return@withContext Result.failure(clientRes.exceptionOrNull() ?: Exception("فشل بدء جلسة TDLib"))
        }
        val client = clientRes.getOrThrow()
        client.setAuthenticationPhoneNumber(phoneNumber)
    }

    suspend fun submitTdlibCode(
        tempSessionId: Long,
        code: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val client = tdlibSessionManager.getClient(tempSessionId)
            ?: return@withContext Result.failure(IllegalStateException("لم يتم العثور على جلسة التحقق النشطة"))
        client.checkAuthenticationCode(code)
    }

    suspend fun submitTdlib2FaPassword(
        tempSessionId: Long,
        password: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val client = tdlibSessionManager.getClient(tempSessionId)
            ?: return@withContext Result.failure(IllegalStateException("لم يتم العثور على جلسة التحقق النشطة"))
        client.checkAuthenticationPassword(password)
    }

    suspend fun startTdlibBotAuth(
        tempSessionId: Long,
        apiId: Int,
        apiHash: String,
        botToken: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val clientRes = tdlibSessionManager.startNewAuthentication(tempSessionId, apiId, apiHash)
        if (clientRes.isFailure) {
            return@withContext Result.failure(clientRes.exceptionOrNull() ?: Exception("فشل بدء جلسة TDLib للبوت"))
        }
        val client = clientRes.getOrThrow()
        client.checkAuthenticationBotToken(botToken)
    }

    suspend fun startTdlibQrAuth(
        tempSessionId: Long,
        apiId: Int,
        apiHash: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val clientRes = tdlibSessionManager.startNewAuthentication(tempSessionId, apiId, apiHash)
        if (clientRes.isFailure) {
            return@withContext Result.failure(clientRes.exceptionOrNull() ?: Exception("فشل بدء جلسة TDLib عبر QR"))
        }
        val client = clientRes.getOrThrow()
        client.requestQrCodeAuthentication()
    }

    suspend fun resendTdlibCode(tempSessionId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        val client = tdlibSessionManager.getClient(tempSessionId)
            ?: return@withContext Result.failure(IllegalStateException("لم يتم العثور على جلسة التحقق"))
        client.resendAuthenticationCode()
    }

    suspend fun cancelTdlibAuth(tempSessionId: Long) = withContext(Dispatchers.IO) {
        tdlibSessionManager.cancelAuthentication(tempSessionId)
    }

    suspend fun finalizeTdlibAccount(
        tempSessionId: Long,
        apiId: String,
        apiHash: String,
        botToken: String,
        accountLabel: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val client = tdlibSessionManager.getClient(tempSessionId)
            ?: return@withContext Result.failure(IllegalStateException("لم يتم العثور على جلسة التحقق"))

        val state = client.authState.value
        if (state !is TdApi.AuthorizationStateReady) {
            return@withContext Result.failure(
                IllegalStateException("لم تؤكد خوادم TDLib اكتمال المصادقة بنجاح بعد.")
            )
        }

        val userRes = client.getMe()
        if (userRes.isFailure) {
            return@withContext Result.failure(
                userRes.exceptionOrNull() ?: Exception("فشل جلب بيانات الحساب من تيليجرام")
            )
        }
        val user = userRes.getOrThrow()
        val realUserId = user.id

        // Requirement 8: Block duplicates (same user ID)
        val existing = accountDao.getAccountByTelegramUserId(realUserId)
        if (existing != null) {
            tdlibSessionManager.cancelAuthentication(tempSessionId)
            return@withContext Result.failure(
                IllegalStateException("هذا الحساب مرتبط بالفعل في التطبيق باسم '${existing.accountName}' (معرف المستخدم: $realUserId)")
            )
        }

        val realName = "${user.firstName} ${user.lastName}".trim()
        val realUsername = user.usernames?.editableUsername
            ?: user.usernames?.activeUsernames?.firstOrNull()
            ?: ""
        val realPhone = user.phoneNumber ?: ""
        val isBot = user.type is TdApi.UserTypeBot

        val hasPrimary = accountDao.getPrimaryAccountDirect() != null
        val isPrimary = !hasPrimary

        val entity = TelegramAccountEntity(
            telegramUserId = realUserId,
            accountName = accountLabel.trim().ifBlank { realName.ifBlank { "حساب تيليجرام" } },
            username = realUsername,
            botToken = if (isBot) botToken.trim() else "",
            apiId = apiId.trim(),
            apiHash = "", // Stored securely in EncryptedSharedPreferences
            phoneNumber = realPhone,
            isBot = isBot,
            isPrimary = isPrimary
        )

        val newAccountId = accountDao.insertAccount(entity)
        if (isPrimary) {
            accountDao.clearOtherPrimaryAccounts(newAccountId)
        }

        // Store credentials securely in EncryptedSharedPreferences (Req 9)
        val secureStorage = SecureCredentialsStorage.getInstance(context)
        secureStorage.saveApiHash(newAccountId, apiHash.trim())
        if (botToken.isNotBlank()) {
            secureStorage.saveBotToken(newAccountId, botToken.trim())
        }
        val existingDbKey = secureStorage.getDatabaseEncryptionKey(tempSessionId)
        if (existingDbKey != null) {
            secureStorage.saveDatabaseEncryptionKey(newAccountId, existingDbKey)
        }

        // Seamlessly transfer the active authenticated client to the permanent account ID
        val liveClient = tdlibSessionManager.transferClientToAccount(tempSessionId, newAccountId)
        if (liveClient != null) {
            DiagnosticLogger.logTdlib("تم نقل جلسة TDLib النشطة بنجاح للحساب الدائم #$newAccountId")
        } else {
            tdlibSessionManager.setActiveAccount(newAccountId)
        }

        Result.success(newAccountId)
    }

    suspend fun deleteAccountAndSession(account: TelegramAccountEntity) = withContext(Dispatchers.IO) {
        tdlibSessionManager.logOutAndDestroy(account.id)
        SecureCredentialsStorage.getInstance(context).removeAccountCredentials(account.id)
        fileDao.deleteFilesByAccount(account.id)
        val vaults = vaultDao.getVaultsByAccountDirect(account.id)
        vaults.forEach { v ->
            fileDao.deleteFilesByChannel(v.channelId)
            fileDao.deleteFilesByChannel(normalizeChannelId(v.channelId))
            vaultDao.deleteVault(v)
        }
        accountDao.deleteAccount(account)
    }

    suspend fun ensureTdlibSession(account: TelegramAccountEntity?): TdlibClient? = withContext(Dispatchers.IO) {
        if (account == null) return@withContext null
        val existing = tdlibSessionManager.getClient(account.id)
        if (existing != null && existing.authState.value is TdApi.AuthorizationStateReady) {
            return@withContext existing
        }

        // Check if any client is already ready in memory
        val anyReady = tdlibSessionManager.getAllActiveClients().firstOrNull { it.authState.value is TdApi.AuthorizationStateReady }
        if (anyReady != null) {
            return@withContext anyReady
        }

        val apiId = account.apiId?.toIntOrNull() ?: 2040
        val apiHash = account.apiHash?.takeIf { it.isNotBlank() }
            ?: SecureCredentialsStorage.getInstance(context).getApiHash(account.id)?.takeIf { it.isNotBlank() }
            ?: "b18441a1ff607e10a989891a5462e627"

        return@withContext tdlibSessionManager.restoreSession(account.id, apiId, apiHash)
    }

    suspend fun restorePrimaryAccountSession() = withContext(Dispatchers.IO) {
        val primary = accountDao.getPrimaryAccountDirect() ?: accountDao.getAllAccountsDirect().firstOrNull()
        if (primary != null && !primary.isBot) {
            ensureTdlibSession(primary)
        }
    }

    private suspend fun getEffectiveBotToken(account: TelegramAccountEntity?): String {
        if (account != null) {
            val direct = account.botToken.trim().removePrefix("bot")
            if (direct.isNotBlank()) return direct

            val secure = SecureCredentialsStorage.getInstance(context).getBotToken(account.id)?.trim()?.removePrefix("bot")
            if (!secure.isNullOrBlank()) return secure
        }

        // Fallback: Check if there is any bot account registered
        val all = accountDao.getAllAccountsDirect()
        val botAcc = all.find { it.isBot && it.botToken.isNotBlank() }
        if (botAcc != null) {
            return botAcc.botToken.trim().removePrefix("bot")
        }
        for (acc in all) {
            val s = SecureCredentialsStorage.getInstance(context).getBotToken(acc.id)?.trim()?.removePrefix("bot")
            if (!s.isNullOrBlank()) return s
        }
        return ""
    }

    suspend fun validateBotToken(token: String): Result<TelegramUser> = withContext(Dispatchers.IO) {
        try {
            val cleanToken = token.trim().removePrefix("bot")
            val api = TelegramClientProvider.getApiService()
            val response = api.getMe(cleanToken)
            if (response.isSuccessful && response.body()?.ok == true && response.body()?.result != null) {
                Result.success(response.body()!!.result!!)
            } else {
                val err = response.body()?.description ?: response.message() ?: "رمز البوت غير صالح"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addAccount(
        accountName: String,
        botToken: String,
        apiId: String? = null,
        apiHash: String? = null,
        phoneNumber: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val cleanToken = botToken.trim().removePrefix("bot")
            val user = validateBotToken(cleanToken).getOrThrow()
            val hasPrimary = accountDao.getPrimaryAccountDirect() != null
            val isPrimary = !hasPrimary

            val entity = TelegramAccountEntity(
                accountName = accountName.ifBlank { user.firstName },
                username = user.username ?: "bot_${user.id}",
                botToken = cleanToken,
                apiId = apiId?.trim(),
                apiHash = apiHash?.trim(),
                phoneNumber = phoneNumber?.trim(),
                isBot = true,
                isPrimary = isPrimary
            )
            val id = accountDao.insertAccount(entity)
            if (isPrimary) {
                accountDao.clearOtherPrimaryAccounts(id)
            }
            SecureCredentialsStorage.getInstance(context).saveBotToken(id, cleanToken)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPrimaryAccount(accountId: Long) = withContext(Dispatchers.IO) {
        val acc = accountDao.getAccountById(accountId) ?: return@withContext
        accountDao.clearOtherPrimaryAccounts(accountId)
        accountDao.updateAccount(acc.copy(isPrimary = true))
    }

    suspend fun deleteAccount(account: TelegramAccountEntity) = withContext(Dispatchers.IO) {
        accountDao.deleteAccount(account)
    }

    suspend fun updateAccountBotToken(accountId: Long, botToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanToken = botToken.trim().removePrefix("bot")
            val account = accountDao.getAccountById(accountId)
                ?: return@withContext Result.failure(Exception("الحساب غير موجود"))
            accountDao.updateAccount(account.copy(botToken = cleanToken))
            SecureCredentialsStorage.getInstance(context).saveBotToken(accountId, cleanToken)
            DiagnosticLogger.log("Account", "تم تحديث وربط رمز البوت للحساب #${accountId} بنجاح")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------
    // VAULT / CHANNEL MANAGEMENT & ADMIN VERIFICATION
    // -------------------------------------------------------------

    private fun parseTelegramError(raw: String, channelId: String, botUsername: String = ""): String {
        return when {
            raw.contains("chat not found", ignoreCase = true) ->
                "لم يتم العثور على القناة ($channelId). تأكد من صحة معرف القناة ومن إضافة البوت (@${botUsername.ifBlank { "bot" }}) كـ Admin فيها."
            raw.contains("not enough rights", ignoreCase = true) || raw.contains("need administrator rights", ignoreCase = true) ->
                "البوت ليس لديه صلاحية إرسال الرسائل في القناة. يرجى الدخول لإعدادات القناة > المشرفون > منح البوت صلاحية نشر الرسائل (Post Messages)."
            raw.contains("not a member", ignoreCase = true) ->
                "البوت ليس عضواً في القناة. يرجى فتح القناة في تيليجرام وإضافة البوت كـ Admin."
            raw.contains("bot was blocked", ignoreCase = true) ->
                "تم حظر البوت في القناة. يرجى إلغاء الحظر وإعادة تعيينه مسؤولاً."
            raw.contains("Unauthorized", ignoreCase = true) ->
                "رمز البوت (Bot Token) غير صالح أو منتهي."
            raw.contains("Request Entity Too Large", ignoreCase = true) ->
                "حجم الملف يتجاوز الحد المسموح به في تيليجرام."
            else -> raw.ifBlank { "فشل التحقق من صلاحيات البوت في القناة ($channelId). تأكد من إضافة البوت كـ Admin." }
        }
    }

    suspend fun verifyChannelAdminStatusWithRetry(
        channelId: String,
        accountId: Long,
        maxRetries: Int = 4,
        initialDelayMs: Long = 1000L,
        onAttemptUpdate: ((attempt: Int, max: Int, status: String) -> Unit)? = null
    ): Result<ChannelVerificationState> = withContext(Dispatchers.IO) {
        val cleanChannelId = channelId.trim().let {
            if (!it.startsWith("-100") && !it.startsWith("@") && it.toLongOrNull() == null) "@$it" else it
        }

        DiagnosticLogger.log(
            tag = "AdminVerification",
            message = "بدء فحص وتأكيد رتبة البوت الإدارية للقناة $cleanChannelId بمحاولات إعادة حتى $maxRetries مرات"
        )

        val account = accountDao.getAccountById(accountId)
            ?: accountDao.getPrimaryAccountDirect()
            ?: accountDao.getAllAccountsDirect().firstOrNull()

        val botToken = getEffectiveBotToken(account)

        // 1. If this is a user account (Phone/QR TDLib) without a bot token:
        if (botToken.isBlank() && account != null && !account.isBot) {
            val client = ensureTdlibSession(account)
            if (client != null && client.authState.value is TdApi.AuthorizationStateReady) {
                DiagnosticLogger.logTdlib("فحص ملكية ورتبة المستخدم في القناة $cleanChannelId عبر محرك TDLib الأصلي")
                val ownershipRes = client.verifyChannelOwnership(cleanChannelId)
                if (ownershipRes.isSuccess) {
                    val info = ownershipRes.getOrThrow()
                    DiagnosticLogger.log(
                        tag = "AdminVerification",
                        message = "تم تأكيد ملكية/إشراف المستخدم على القناة (${info.title}) بنجاح: ${info.statusDescription}",
                        level = DiagnosticLogger.Level.SUCCESS
                    )
                    return@withContext Result.success(
                        ChannelVerificationState(
                            channelId = cleanChannelId,
                            isConfirmedAdmin = true,
                            canPostMessages = true,
                            statusText = info.statusDescription,
                            attemptsDone = 1
                        )
                    )
                } else {
                    val err = ownershipRes.exceptionOrNull()?.localizedMessage
                        ?: "أنت لست مشرفاً أو مالكاً لهذه القناة على تيليجرام"
                    DiagnosticLogger.logError("AdminVerification", err)
                    return@withContext Result.failure(Exception(err))
                }
            } else {
                val err = "جلسة تيليجرام للمستخدم (${account.accountName}) غير متصلة حالياً. يرجى فتح شاشة الحسابات وتأكيد الاتصال."
                DiagnosticLogger.logError("AdminVerification", err)
                return@withContext Result.failure(Exception(err))
            }
        }

        // 2. If no bot token is found and account is bot or null:
        if (botToken.isBlank()) {
            val err = "لا يتوفر رمز بوت (Bot Token) أو حساب تيليجرام مسجل للتحقق من القناة."
            DiagnosticLogger.logError("AdminVerification", err)
            return@withContext Result.failure(Exception(err))
        }

        val api = TelegramClientProvider.getApiService()
        var lastError: String = ""

        // 1. Get Bot identity
        var botUserId: Long = 0L
        var botUsername: String = ""
        try {
            val meResp = api.getMe(botToken)
            if (meResp.isSuccessful && meResp.body()?.ok == true && meResp.body()?.result != null) {
                val botUser = meResp.body()!!.result!!
                botUserId = botUser.id
                botUsername = botUser.username ?: ""
                DiagnosticLogger.log(
                    tag = "AdminVerification",
                    message = "تم التعرف على هوية البوت: @$botUsername (ID: $botUserId)"
                )
            } else {
                val rawMeErr = try { meResp.errorBody()?.string() } catch (e: Exception) { null }
                DiagnosticLogger.logError("AdminVerification", "فشل استعلام getMe للبوت", rawResponse = rawMeErr)
            }
        } catch (e: Exception) {
            DiagnosticLogger.logError("AdminVerification", "استثناء أثناء استعلام getMe", throwable = e)
        }

        for (attempt in 1..maxRetries) {
            onAttemptUpdate?.invoke(attempt, maxRetries, "جارٍ الفحص (محاولة $attempt من $maxRetries)...")
            DiagnosticLogger.logBotAdminCheck(cleanChannelId, attempt, maxRetries, "جاري فحص القناة وعضوية البوت...")

            try {
                // 1. Check getChat
                val chatResp = api.getChat(botToken, cleanChannelId)
                if (!chatResp.isSuccessful || chatResp.body()?.ok != true) {
                    val rawErr = try { chatResp.errorBody()?.string() } catch (e: Exception) { null }
                    val desc = rawErr ?: chatResp.body()?.description ?: "فشل الوصول للقناة"
                    lastError = desc
                    DiagnosticLogger.logBotAdminCheck(
                        cleanChannelId, attempt, maxRetries,
                        "فشل getChat: $desc",
                        DiagnosticLogger.Level.WARN,
                        rawErr
                    )

                    if (attempt < maxRetries) {
                        delay(initialDelayMs * attempt)
                        continue
                    } else {
                        val nice = parseTelegramError(desc, cleanChannelId, botUsername)
                        return@withContext Result.failure(Exception(nice))
                    }
                }

                val chat = chatResp.body()!!.result!!
                DiagnosticLogger.logBotAdminCheck(
                    cleanChannelId, attempt, maxRetries,
                    "تم العثور على القناة '${chat.title}' (النوع: ${chat.type}). جاري فحص صلاحيات الأدمن...",
                    DiagnosticLogger.Level.INFO
                )

                // 2. Check getChatMember if we have botUserId
                if (botUserId > 0) {
                    val memberResp = api.getChatMember(botToken, cleanChannelId, botUserId)
                    if (memberResp.isSuccessful && memberResp.body()?.ok == true && memberResp.body()?.result != null) {
                        val member = memberResp.body()!!.result!!
                        val st = member.status.lowercase()
                        val isAdmin = st == "administrator" || st == "creator"
                        val canPost = if (st == "creator") true else (member.canPostMessages ?: true)

                        if (isAdmin) {
                            DiagnosticLogger.logBotAdminCheck(
                                cleanChannelId, attempt, maxRetries,
                                "تم تأكيد رتبة البوت الإدارية بنجاح! الرتبة: $st | إرسال الرسائل: $canPost",
                                DiagnosticLogger.Level.SUCCESS
                            )
                            return@withContext Result.success(
                                ChannelVerificationState(
                                    channelId = cleanChannelId,
                                    isConfirmedAdmin = true,
                                    canPostMessages = canPost,
                                    statusText = "البوت مسؤول نشط ومؤكد في القناة ✅",
                                    attemptsDone = attempt
                                )
                            )
                        } else {
                            val msg = "البوت مسجل في القناة برتبة ($st) وليس كمسؤول (Admin)!"
                            DiagnosticLogger.logBotAdminCheck(
                                cleanChannelId, attempt, maxRetries,
                                msg,
                                DiagnosticLogger.Level.WARN
                            )
                            lastError = msg
                            if (attempt < maxRetries) {
                                delay(initialDelayMs * attempt)
                                continue
                            } else {
                                return@withContext Result.failure(
                                    Exception("البوت (@$botUsername) موجود في القناة برتبة ($st) ولكن ليس مسؤولاً. يرجى ترقية البوت إلى Admin في القناة وتفعيل صلاحية نشر الرسائل.")
                                )
                            }
                        }
                    } else {
                        val rawMemErr = try { memberResp.errorBody()?.string() } catch (e: Exception) { null }
                        val desc = rawMemErr ?: "فشل التحقق من عضوية البوت"
                        lastError = desc
                        DiagnosticLogger.logBotAdminCheck(
                            cleanChannelId, attempt, maxRetries,
                            "فشل getChatMember: $desc",
                            DiagnosticLogger.Level.WARN,
                            rawMemErr
                        )
                        if (attempt < maxRetries) {
                            delay(initialDelayMs * attempt)
                            continue
                        }
                    }
                } else {
                    // Bot user id not known, but getChat succeeded (which requires bot to be in or see chat)
                    DiagnosticLogger.logBotAdminCheck(
                        cleanChannelId, attempt, maxRetries,
                        "تم الوصول للقناة بنجاح.",
                        DiagnosticLogger.Level.SUCCESS
                    )
                    return@withContext Result.success(
                        ChannelVerificationState(
                            channelId = cleanChannelId,
                            isConfirmedAdmin = true,
                            canPostMessages = true,
                            statusText = "تم التحقق من القناة بنجاح ✅",
                            attemptsDone = attempt
                        )
                    )
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "استثناء أثناء الاتصال"
                DiagnosticLogger.logError("AdminVerification", "خطأ في المحاولة $attempt", throwable = e)
                if (attempt < maxRetries) {
                    delay(initialDelayMs * attempt)
                }
            }
        }

        val nice = parseTelegramError(lastError, cleanChannelId, botUsername)
        Result.failure(Exception(nice))
    }

    suspend fun testChannelConnection(
        accountId: Long,
        channelId: String,
        explicitBotToken: String? = null
    ): ChannelTestResult = withContext(Dispatchers.IO) {
        val normId = normalizeChannelId(channelId)
        if (normId.isBlank()) {
            return@withContext ChannelTestResult(
                isSuccess = false,
                errorMessage = "يرجى إدخال معرف قناة صالح (مثل @my_channel أو -100...)"
            )
        }

        val account = accountDao.getAccountById(accountId)
            ?: accountDao.getPrimaryAccountDirect()
            ?: accountDao.getAllAccountsDirect().firstOrNull()

        // 1. If account is a user account (Phone / QR session), verify directly via native TDLib
        if (account != null && !account.isBot) {
            val tdClient = ensureTdlibSession(account)
            if (tdClient != null && tdClient.authState.value is TdApi.AuthorizationStateReady) {
                DiagnosticLogger.logTdlib("فحص ملكية القناة $normId عبر محرك TDLib لحساب المستخدم (${account.accountName})")
                val ownershipRes = tdClient.verifyChannelOwnership(normId)
                if (ownershipRes.isSuccess) {
                    val info = ownershipRes.getOrThrow()
                    DiagnosticLogger.log(
                        tag = "AdminVerification",
                        message = "تم تأكيد ملكية القناة بنجاح: ${info.title} (Chat ID: ${info.chatId})",
                        level = DiagnosticLogger.Level.SUCCESS
                    )
                    return@withContext ChannelTestResult(
                        isSuccess = true,
                        channelTitle = info.title,
                        normalizedChannelId = info.chatId.toString()
                    )
                } else {
                    val err = ownershipRes.exceptionOrNull()?.localizedMessage
                        ?: "أنت لست مشرفاً أو مالكاً لهذه القناة على تيليجرام"
                    DiagnosticLogger.logError("AdminVerification", err)
                    return@withContext ChannelTestResult(
                        isSuccess = false,
                        normalizedChannelId = normId,
                        errorMessage = err
                    )
                }
            } else {
                return@withContext ChannelTestResult(
                    isSuccess = false,
                    normalizedChannelId = normId,
                    errorMessage = "جلسة تيليجرام غير متصلة حالياً. يرجى التأكد من تسجيل الدخول أولاً."
                )
            }
        }

        // 2. Telegram Bot API verification for Bot accounts
        val token = explicitBotToken?.trim()?.removePrefix("bot")?.takeIf { it.isNotBlank() }
            ?: getEffectiveBotToken(account)

        if (token.isNotBlank()) {
            val verifyRes = verifyChannelAdminStatusWithRetry(normId, accountId, maxRetries = 3)
            if (verifyRes.isSuccess) {
                var title = "خزينة $normId"
                try {
                    val api = TelegramClientProvider.getApiService()
                    val chatResp = api.getChat(token, normId)
                    if (chatResp.isSuccessful && chatResp.body()?.ok == true) {
                        title = chatResp.body()?.result?.title ?: title
                    }
                } catch (_: Exception) {}

                return@withContext ChannelTestResult(
                    isSuccess = true,
                    channelTitle = title,
                    normalizedChannelId = normId
                )
            } else {
                val err = verifyRes.exceptionOrNull()?.localizedMessage ?: "فشل التحقق من صلاحيات البوت في القناة"
                return@withContext ChannelTestResult(
                    isSuccess = false,
                    normalizedChannelId = normId,
                    errorMessage = err
                )
            }
        }

        ChannelTestResult(
            isSuccess = false,
            normalizedChannelId = normId,
            errorMessage = "لا يتوفر رمز بوت (Bot Token) أو حساب مستخدم نشط لفحص القناة."
        )
    }

    suspend fun verifyAndAddVault(
        channelId: String,
        vaultTitle: String,
        accountId: Long,
        isEncrypted: Boolean = false,
        isDefault: Boolean = false,
        explicitBotToken: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val acc = accountDao.getAccountById(accountId)
                ?: accountDao.getPrimaryAccountDirect()
                ?: accountDao.getAllAccountsDirect().firstOrNull()
                ?: return@withContext Result.failure(Exception("الحساب المحدد غير موجود"))

            if (!explicitBotToken.isNullOrBlank()) {
                val clean = explicitBotToken.trim().removePrefix("bot")
                accountDao.updateAccount(acc.copy(botToken = clean))
                SecureCredentialsStorage.getInstance(context).saveBotToken(acc.id, clean)
            }

            val cleanChannelId = normalizeChannelId(channelId)

            // STRICT REAL VERIFICATION BEFORE ADDING TO DATABASE
            val testRes = testChannelConnection(acc.id, cleanChannelId, explicitBotToken)
            if (!testRes.isSuccess) {
                DiagnosticLogger.logError("AdminVerification", "رفض إضافة القناة: ${testRes.errorMessage}")
                return@withContext Result.failure(Exception(testRes.errorMessage))
            }

            val finalChannelId = testRes.normalizedChannelId.ifBlank { cleanChannelId }
            val resolvedTitle = if (vaultTitle.isNotBlank()) vaultTitle else testRes.channelTitle

            // Prevent duplicate vaults
            val existing = vaultDao.getVaultByChannelId(finalChannelId)
            if (existing != null) {
                return@withContext Result.failure(Exception("هذه القناة مضافة بالفعل كخزينة تخزين باسم (${existing.channelTitle})."))
            }

            val hasDefault = vaultDao.getDefaultVaultDirect() != null
            val shouldBeDefault = isDefault || !hasDefault

            val vault = DriveVaultEntity(
                channelId = finalChannelId,
                channelTitle = resolvedTitle.ifBlank { "خزينة $finalChannelId" },
                accountId = acc.id,
                isEncrypted = isEncrypted,
                salt = if (isEncrypted) CryptoEngine.hashPassword(UUID.randomUUID().toString(), "vault_salt") else "",
                isDefault = shouldBeDefault
            )
            val id = vaultDao.insertVault(vault)
            if (shouldBeDefault) {
                vaultDao.clearOtherDefaultVaults(id)
            }
            DiagnosticLogger.log(
                tag = "AdminVerification",
                message = "تم حفظ قناة التخزين بنجاح بعد التحقق الحقيقي: $resolvedTitle ($finalChannelId)",
                level = DiagnosticLogger.Level.SUCCESS
            )

            // Auto sync past messages/files from Telegram cloud for this channel
            try {
                syncVaultFilesFromTelegram(id)
            } catch (_: Exception) {}

            Result.success(id)
        } catch (e: Exception) {
            DiagnosticLogger.logError("AdminVerification", "استثناء أثناء إضافة القناة", throwable = e)
            Result.failure(e)
        }
    }

    suspend fun syncVaultFilesFromTelegram(vaultId: Long): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val vault = vaultDao.getVaultById(vaultId)
                ?: return@withContext Result.failure(Exception("الخزينة غير موجودة"))

            val account = accountDao.getAccountById(vault.accountId)
                ?: accountDao.getPrimaryAccountDirect()
                ?: accountDao.getAllAccountsDirect().firstOrNull()
                ?: return@withContext Result.failure(Exception("الحساب غير موجود"))

            val cleanChannelId = normalizeChannelId(vault.channelId)

            var tdClient = tdlibSessionManager.getClient(account.id)
            if (tdClient == null || tdClient.authState.value !is TdApi.AuthorizationStateReady) {
                tdClient = ensureTdlibSession(account)
            }

            if (tdClient == null || tdClient.authState.value !is TdApi.AuthorizationStateReady) {
                return@withContext Result.failure(Exception("جلسة تيليجرام غير متصلة للمزامنة"))
            }

            val resolveRes = tdClient.resolveChatId(cleanChannelId)
            val chatId = resolveRes.getOrNull() ?: return@withContext Result.failure(Exception("تعذر الوصول للقناة عبر تيليجرام"))

            val historyRes = tdClient.getChatHistory(chatId, fromMessageId = 0L, limit = 100)
            if (historyRes.isFailure) {
                return@withContext Result.failure(historyRes.exceptionOrNull() ?: Exception("فشل جلب سجل القناة من خوادم تيليجرام"))
            }

            val messages = historyRes.getOrThrow().messages
            var restoredCount = 0

            for (msg in messages) {
                val content = msg.content
                if (content is TdApi.MessageDocument) {
                    val doc = content.document
                    val fileName = doc.fileName.ifBlank { "file_${msg.id}" }
                    val isEnc = fileName.endsWith(".tldrv")
                    val origName = if (isEnc) fileName.removeSuffix(".tldrv") else fileName
                    val ext = origName.substringAfterLast('.', "")
                    val mime = doc.mimeType.ifBlank { "application/octet-stream" }
                    val size = doc.document.size.toLong()
                    val remoteId = doc.document.remote.id.ifBlank { "td_${msg.id}" }

                    val existing = fileDao.getFileByTelegramFileIdOrMessageId(remoteId, msg.id)
                    if (existing == null) {
                        val fileEntity = DriveFileEntity(
                            remoteMessageId = msg.id,
                            telegramFileId = remoteId,
                            telegramFileUniqueId = UUID.randomUUID().toString(),
                            fileName = fileName,
                            originalFileName = origName,
                            extension = ext,
                            mimeType = mime,
                            sizeBytes = size,
                            channelId = cleanChannelId,
                            accountId = account.id,
                            virtualFolderPath = "/",
                            isEncrypted = isEnc,
                            encryptionAlgorithm = if (isEnc) "AES-256-GCM" else "NONE",
                            uploadStatus = "COMPLETED"
                        )
                        fileDao.insertFile(fileEntity)
                        restoredCount++
                    }
                }
            }

            if (restoredCount > 0) {
                val allChannelFiles = fileDao.getAllFilesDirect().filter { it.channelId == cleanChannelId }
                vaultDao.updateVault(vault.copy(totalFiles = allChannelFiles.size, totalBytes = allChannelFiles.sumOf { it.sizeBytes }))
            }

            DiagnosticLogger.log(
                tag = "ChannelSync",
                message = "تمت مزامنة القناة ($cleanChannelId) بنجاح وإعادة $restoredCount ملفاً من سيرفرات تيليجرام السحابية ✅",
                level = DiagnosticLogger.Level.SUCCESS
            )

            Result.success(restoredCount)
        } catch (e: Exception) {
            DiagnosticLogger.logError("ChannelSync", "خطأ أثناء مزامنة القناة", throwable = e)
            Result.failure(e)
        }
    }

    suspend fun setDefaultVault(vaultId: Long) = withContext(Dispatchers.IO) {
        val v = vaultDao.getVaultById(vaultId) ?: return@withContext
        vaultDao.clearOtherDefaultVaults(vaultId)
        vaultDao.updateVault(v.copy(isDefault = true))
    }

    suspend fun deleteVault(vault: DriveVaultEntity) = withContext(Dispatchers.IO) {
        fileDao.deleteFilesByChannel(vault.channelId)
        fileDao.deleteFilesByChannel(normalizeChannelId(vault.channelId))
        vaultDao.deleteVault(vault)
    }

    // -------------------------------------------------------------
    // FILE UPLOADER ENGINE (WITH CHUNKING & E2EE)
    // -------------------------------------------------------------

    suspend fun uploadFile(
        fileUri: Uri,
        virtualFolder: String = "/",
        isEncrypted: Boolean = false,
        encryptionPassword: String = "",
        targetVaultId: Long? = null,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<DriveFileEntity> = withContext(Dispatchers.IO) {
        var tempSourceFile: File? = null
        var processedFileToUpload: File? = null
        val chunkTempFiles = mutableListOf<File>()

        try {
            // 1. Resolve Vault and Account
            val vault = if (targetVaultId != null) {
                vaultDao.getVaultById(targetVaultId)
            } else {
                vaultDao.getDefaultVaultDirect() ?: vaultDao.getAllVaults().firstOrNull()?.firstOrNull()
            } ?: return@withContext Result.failure(Exception("لم يتم العثور على قناة/خزينة تخزين. يرجى إضافة قناة تخزين أولاً في شاشة الحسابات."))

            val account = accountDao.getAccountById(vault.accountId)
                ?: accountDao.getPrimaryAccountDirect()
                ?: accountDao.getAllAccountsDirect().firstOrNull()
                ?: return@withContext Result.failure(Exception("حساب تيليجرام المرتبط بهذه القناة غير موجود."))

            val botToken = getEffectiveBotToken(account)
            var tdClient = tdlibSessionManager.getClient(account.id)
            if (tdClient == null || tdClient.authState.value !is TdApi.AuthorizationStateReady) {
                tdClient = ensureTdlibSession(account)
            }
            val isTdlibReady = tdClient != null && tdClient.authState.value is TdApi.AuthorizationStateReady

            if (botToken.isBlank() && !isTdlibReady && account.isBot) {
                return@withContext Result.failure(
                    Exception("لا يوجد رمز Bot Token صالح أو جلسة TDLib نشطة لرفع الملفات إلى هذه القناة. يرجى إضافة حساب بوت أو تسجيل الدخول برقم الهاتف في شاشة الحسابات.")
                )
            }

            val targetChannelId = normalizeChannelId(vault.channelId)

            // 2. Extract metadata from URI
            var originalName = "file_${System.currentTimeMillis()}"
            var fileSize = 0L
            var mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"

            context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) originalName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val extension = originalName.substringAfterLast('.', "")

            // 3. Copy URI to temp cache file
            tempSourceFile = File(context.cacheStreamDir(), "src_${UUID.randomUUID()}_$originalName")
            context.contentResolver.openInputStream(fileUri)?.use { input ->
                FileOutputStream(tempSourceFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("فشل قراءة الملف من الجهاز."))

            fileSize = tempSourceFile.length()
            var sha256 = CryptoEngine.calculateFileSha256(tempSourceFile)

            // 4. Handle Zero-Knowledge E2EE Encryption
            var uploadFileName = originalName
            if (isEncrypted) {
                if (encryptionPassword.isBlank()) {
                    return@withContext Result.failure(Exception("كلمة مرور التشفير مطلوبة لتشفير هذا الملف."))
                }
                onProgress(
                    TransferProgress(
                        fileName = originalName,
                        bytesTransferred = 0,
                        totalBytes = fileSize,
                        isEncrypting = true,
                        status = "جارٍ تشفير الملف بتشفير AES-256-GCM..."
                    )
                )
                val encryptedFile = File(context.cacheStreamDir(), "enc_${UUID.randomUUID()}_$originalName.enc")
                sha256 = CryptoEngine.encryptFile(tempSourceFile, encryptedFile, encryptionPassword) { processed, total ->
                    onProgress(
                        TransferProgress(
                            fileName = originalName,
                            bytesTransferred = processed,
                            totalBytes = total,
                            isEncrypting = true,
                            status = "جارٍ التشفير: ${(processed * 100 / total)}%"
                        )
                    )
                }
                uploadFileName = "$originalName.tldrv"
                processedFileToUpload = encryptedFile
            } else {
                processedFileToUpload = tempSourceFile
            }

            val finalFileLength = processedFileToUpload.length()
            val isChunked = finalFileLength > CHUNK_SIZE_BYTES
            val totalChunks = if (isChunked) {
                ((finalFileLength + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES).toInt()
            } else 1

            val api = TelegramClientProvider.getApiService()
            var totalBytesUploaded = 0L
            val startTime = System.currentTimeMillis()

            // Handle direct TDLib native upload (for Phone / QR code user accounts without a bot token)
            if (botToken.isBlank() && isTdlibReady && tdClient != null) {
                DiagnosticLogger.logTdlib("بدء رفع الملف $originalName عبر محرك TDLib الأصلي (القناة: $targetChannelId)")
                onProgress(
                    TransferProgress(
                        fileName = originalName,
                        bytesTransferred = 0,
                        totalBytes = finalFileLength,
                        currentChunk = 1,
                        totalChunks = 1,
                        status = "جارٍ الرفع السحابي عبر محرك TDLib..."
                    )
                )

                val resolveRes = tdClient.resolveChatId(targetChannelId)
                val chatId = if (resolveRes.isSuccess) {
                    resolveRes.getOrThrow()
                } else {
                    targetChannelId.toLongOrNull() ?: return@withContext Result.failure(
                        Exception("تعذر العثور على القناة عبر TDLib ($targetChannelId): ${resolveRes.exceptionOrNull()?.localizedMessage}")
                    )
                }

                val caption = "📂 TeleDrive: $originalName\n🔒 E2EE: $isEncrypted\n⚡ SHA-256: ${sha256.take(8)}..."
                val tdResult = tdClient.sendDocument(chatId, processedFileToUpload.absolutePath, caption)

                if (tdResult.isFailure) {
                    val ex = tdResult.exceptionOrNull() ?: Exception("فشل رفع الملف عبر TDLib")
                    DiagnosticLogger.logError("UploadStream", "فشل الرفع عبر TDLib", throwable = ex)
                    return@withContext Result.failure(ex)
                }

                val msg = tdResult.getOrThrow()
                val doc = (msg.content as? org.drinkless.tdlib.TdApi.MessageDocument)?.document
                val remoteId = doc?.document?.remote?.id ?: "tdlib_${msg.id}"
                val remoteUniqueId = doc?.document?.remote?.uniqueId ?: UUID.randomUUID().toString()

                val fileEntity = DriveFileEntity(
                    remoteMessageId = msg.id,
                    telegramFileId = remoteId,
                    telegramFileUniqueId = remoteUniqueId,
                    fileName = uploadFileName,
                    originalFileName = originalName,
                    extension = extension,
                    mimeType = mimeType,
                    sizeBytes = fileSize,
                    channelId = targetChannelId,
                    accountId = account.id,
                    virtualFolderPath = virtualFolder,
                    isEncrypted = isEncrypted,
                    encryptionAlgorithm = if (isEncrypted) "AES-256-GCM" else "NONE",
                    sha256Checksum = sha256,
                    isChunked = false,
                    totalChunks = 1,
                    uploadStatus = "COMPLETED"
                )
                val savedId = fileDao.insertFile(fileEntity)
                vaultDao.updateVault(vault.copy(totalFiles = vault.totalFiles + 1, totalBytes = vault.totalBytes + fileSize))
                DiagnosticLogger.logUploadStream(originalName, "تم الرفع بنجاح عبر TDLib (Message ID: ${msg.id})", DiagnosticLogger.Level.SUCCESS)
                return@withContext Result.success(fileEntity.copy(id = savedId))
            }

            if (!isChunked) {
                // Single File Upload
                onProgress(
                    TransferProgress(
                        fileName = originalName,
                        bytesTransferred = 0,
                        totalBytes = finalFileLength,
                        currentChunk = 1,
                        totalChunks = 1,
                        status = "جارٍ الرفع إلى تيليجرام..."
                    )
                )

                val fileReqBody = processedFileToUpload.asRequestBody(mimeType.toMediaTypeOrNull())
                val progressBody = ProgressRequestBody(fileReqBody) { written, total ->
                    val elapsedSec = (System.currentTimeMillis() - startTime).coerceAtLeast(1) / 1000.0
                    val speed = (written / elapsedSec).toLong()
                    onProgress(
                        TransferProgress(
                            fileName = originalName,
                            bytesTransferred = written,
                            totalBytes = total,
                            speedBytesPerSec = speed,
                            status = "جارٍ الرفع السحابي: ${(written * 100 / total)}%"
                        )
                    )
                }

                val docPart = MultipartBody.Part.createFormData("document", uploadFileName, progressBody)
                val captionBody = "📂 TeleDrive: $originalName\n🔒 E2EE: $isEncrypted\n⚡ SHA-256: ${sha256.take(8)}..."
                    .toRequestBody("text/plain".toMediaTypeOrNull())

                DiagnosticLogger.logUploadStream(originalName, "إرسال الملف إلى تيليجرام (القناة: $targetChannelId)...")
                val response = api.sendDocument(botToken, targetChannelId, docPart, captionBody)
                if (!response.isSuccessful || response.body()?.ok != true) {
                    val rawErr = try {
                        response.errorBody()?.string()
                    } catch (e: Exception) { null } ?: response.body()?.description ?: "خطأ HTTP ${response.code()}"

                    DiagnosticLogger.logError(
                        tag = "UploadStream",
                        message = "فشل رفع الملف إلى تيليجرام (HTTP ${response.code()})",
                        rawResponse = rawErr
                    )

                    val niceErr = parseTelegramError(rawErr, targetChannelId)
                    return@withContext Result.failure(Exception(niceErr))
                }

                val msg = response.body()!!.result!!
                val doc = msg.document ?: return@withContext Result.failure(Exception("لم يُرجع تيليجرام مستنداً صالحاً"))

                val fileEntity = DriveFileEntity(
                    remoteMessageId = msg.messageId,
                    telegramFileId = doc.fileId,
                    telegramFileUniqueId = doc.fileUniqueId,
                    fileName = uploadFileName,
                    originalFileName = originalName,
                    extension = extension,
                    mimeType = mimeType,
                    sizeBytes = fileSize,
                    channelId = targetChannelId,
                    accountId = account.id,
                    virtualFolderPath = virtualFolder,
                    isEncrypted = isEncrypted,
                    encryptionAlgorithm = if (isEncrypted) "AES-256-GCM" else "NONE",
                    sha256Checksum = sha256,
                    isChunked = false,
                    totalChunks = 1,
                    uploadStatus = "COMPLETED"
                )
                val savedId = fileDao.insertFile(fileEntity)
                val createdEntity = fileEntity.copy(id = savedId)

                // Update vault stats
                vaultDao.updateVault(vault.copy(totalFiles = vault.totalFiles + 1, totalBytes = vault.totalBytes + fileSize))
                DiagnosticLogger.logUploadStream(
                    originalName,
                    "تم الرفع بنجاح وحفظ الملف في قاعدة البيانات (Message ID: ${msg.messageId})",
                    DiagnosticLogger.Level.SUCCESS
                )

                Result.success(createdEntity)
            } else {
                // Multi-Chunk Upload Engine
                val baseFileRecord = DriveFileEntity(
                    remoteMessageId = 0L,
                    telegramFileId = "chunked_multi",
                    telegramFileUniqueId = UUID.randomUUID().toString(),
                    fileName = uploadFileName,
                    originalFileName = originalName,
                    extension = extension,
                    mimeType = mimeType,
                    sizeBytes = fileSize,
                    channelId = targetChannelId,
                    accountId = account.id,
                    virtualFolderPath = virtualFolder,
                    isEncrypted = isEncrypted,
                    encryptionAlgorithm = if (isEncrypted) "AES-256-GCM" else "NONE",
                    sha256Checksum = sha256,
                    isChunked = true,
                    totalChunks = totalChunks,
                    uploadStatus = "UPLOADING"
                )
                val fileDbId = fileDao.insertFile(baseFileRecord)
                val chunkEntities = mutableListOf<FileChunkEntity>()

                FileInputStream(processedFileToUpload).use { fis ->
                    val buffer = ByteArray(CHUNK_SIZE_BYTES.toInt())
                    var chunkIdx = 0

                    while (true) {
                        val readBytes = fis.read(buffer)
                        if (readBytes <= 0) break

                        val chunkFile = File(context.cacheStreamDir(), "chk_${UUID.randomUUID()}_${chunkIdx}_$uploadFileName")
                        chunkTempFiles.add(chunkFile)

                        FileOutputStream(chunkFile).use { fos ->
                            fos.write(buffer, 0, readBytes)
                        }

                        val chunkSha = CryptoEngine.calculateFileSha256(chunkFile)
                        val chunkPartName = "$uploadFileName.part${chunkIdx + 1}_of_$totalChunks"

                        val fileReqBody = chunkFile.asRequestBody(mimeType.toMediaTypeOrNull())
                        val progressBody = ProgressRequestBody(fileReqBody) { written, _ ->
                            val currentUploaded = totalBytesUploaded + written
                            val elapsedSec = (System.currentTimeMillis() - startTime).coerceAtLeast(1) / 1000.0
                            val speed = (currentUploaded / elapsedSec).toLong()
                            onProgress(
                                TransferProgress(
                                    fileId = fileDbId,
                                    fileName = originalName,
                                    bytesTransferred = currentUploaded,
                                    totalBytes = finalFileLength,
                                    speedBytesPerSec = speed,
                                    currentChunk = chunkIdx + 1,
                                    totalChunks = totalChunks,
                                    status = "رفع الجزء (${chunkIdx + 1} / $totalChunks)"
                                )
                            )
                        }

                        val docPart = MultipartBody.Part.createFormData("document", chunkPartName, progressBody)
                        val captionBody = "🧩 TeleDrive Chunk: $chunkPartName\n⚡ Checksum: ${chunkSha.take(8)}"
                            .toRequestBody("text/plain".toMediaTypeOrNull())

                        DiagnosticLogger.logUploadStream(originalName, "إرسال الجزء ${chunkIdx + 1}/$totalChunks...")
                        val response = api.sendDocument(botToken, targetChannelId, docPart, captionBody)
                        if (!response.isSuccessful || response.body()?.ok != true) {
                            fileDao.deleteFileById(fileDbId)
                            val rawErr = try {
                                response.errorBody()?.string()
                            } catch (e: Exception) { null } ?: response.body()?.description ?: "خطأ HTTP ${response.code()}"

                            DiagnosticLogger.logError(
                                tag = "UploadStream",
                                message = "فشل رفع الجزء ${chunkIdx + 1}/$totalChunks (HTTP ${response.code()})",
                                rawResponse = rawErr
                            )

                            val niceErr = parseTelegramError(rawErr, targetChannelId)
                            return@withContext Result.failure(Exception("فشل رفع الجزء رقم ${chunkIdx + 1}: $niceErr"))
                        }

                        val msg = response.body()!!.result!!
                        val doc = msg.document!!

                        chunkEntities.add(
                            FileChunkEntity(
                                fileId = fileDbId,
                                chunkIndex = chunkIdx,
                                totalChunks = totalChunks,
                                remoteMessageId = msg.messageId,
                                telegramFileId = doc.fileId,
                                chunkSizeBytes = readBytes.toLong(),
                                sha256 = chunkSha
                            )
                        )

                        totalBytesUploaded += readBytes
                        chunkIdx++
                    }
                }

                chunkDao.insertChunks(chunkEntities)
                val completedFile = baseFileRecord.copy(
                    id = fileDbId,
                    uploadStatus = "COMPLETED",
                    telegramFileId = chunkEntities.firstOrNull()?.telegramFileId ?: ""
                )
                fileDao.updateFile(completedFile)
                vaultDao.updateVault(vault.copy(totalFiles = vault.totalFiles + 1, totalBytes = vault.totalBytes + fileSize))

                Result.success(completedFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempSourceFile?.delete()
            processedFileToUpload?.delete()
            chunkTempFiles.forEach { it.delete() }
        }
    }

    // -------------------------------------------------------------
    // FILE DOWNLOADER & STREAMING ENGINE
    // -------------------------------------------------------------

    suspend fun downloadOrStreamFile(
        file: DriveFileEntity,
        destinationFile: File? = null,
        decryptionPassword: String = "",
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempEncryptedChunks = mutableListOf<File>()
        var assembledRawFile: File? = null

        try {
            val account = accountDao.getAccountById(file.accountId)
                ?: return@withContext Result.failure(Exception("حساب تيليجرام غير متصل"))

            val botToken = getEffectiveBotToken(account)
            if (botToken.isBlank()) {
                return@withContext Result.failure(Exception("لا يوجد رمز Bot Token صالح لتنزيل الملف من تيليجرام."))
            }

            val api = TelegramClientProvider.getApiService()
            val targetOutput = destinationFile ?: File(context.cacheStreamDir(), "stream_${file.id}_${file.originalFileName}")

            if (!file.isChunked) {
                // 1. Get direct Telegram file download link
                val fileInfoResp = api.getFile(botToken, file.telegramFileId)
                if (!fileInfoResp.isSuccessful || fileInfoResp.body()?.ok != true) {
                    return@withContext Result.failure(Exception("فشل الحصول على مسار الملف من تيليجرام"))
                }
                val tgFile = fileInfoResp.body()!!.result!!
                val downloadUrl = TelegramClientProvider.getDirectDownloadUrl(botToken, tgFile.filePath ?: "")

                val rawDownloadTemp = File(context.cacheStreamDir(), "raw_${UUID.randomUUID()}_${file.fileName}")
                val downloadResp = api.downloadFileDirectly(downloadUrl)
                if (!downloadResp.isSuccessful || downloadResp.body() == null) {
                    return@withContext Result.failure(Exception("فشل تنزيل الملف من سيرفرات تيليجرام"))
                }

                val body = downloadResp.body()!!
                val contentLength = body.contentLength()
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(rawDownloadTemp).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(
                                TransferProgress(
                                    fileId = file.id,
                                    fileName = file.originalFileName,
                                    bytesTransferred = downloaded,
                                    totalBytes = contentLength,
                                    status = "جارٍ التحميل: ${(downloaded * 100 / contentLength.coerceAtLeast(1))}%"
                                )
                            )
                        }
                    }
                }

                if (file.isEncrypted) {
                    if (decryptionPassword.isBlank()) {
                        rawDownloadTemp.delete()
                        return@withContext Result.failure(Exception("كلمة مرور فك التشفير مطلوبة لعرض أو تشغيل هذا الملف."))
                    }
                    onProgress(
                        TransferProgress(
                            fileId = file.id,
                            fileName = file.originalFileName,
                            isDecrypting = true,
                            status = "جارٍ فك تشفير AES-256-GCM..."
                        )
                    )
                    CryptoEngine.decryptFile(rawDownloadTemp, targetOutput, decryptionPassword) { proc, tot ->
                        onProgress(
                            TransferProgress(
                                fileId = file.id,
                                fileName = file.originalFileName,
                                bytesTransferred = proc,
                                totalBytes = tot,
                                isDecrypting = true,
                                status = "جارٍ فك التشفير: ${(proc * 100 / tot)}%"
                            )
                        )
                    }
                    rawDownloadTemp.delete()
                } else {
                    rawDownloadTemp.renameTo(targetOutput)
                }

                Result.success(targetOutput)
            } else {
                // Chunked File Re-assembly
                val chunks = chunkDao.getChunksForFile(file.id)
                if (chunks.isEmpty()) {
                    return@withContext Result.failure(Exception("لم يتم العثور على أجزاء الملف في قاعدة البيانات."))
                }

                assembledRawFile = File(context.cacheStreamDir(), "assembled_${UUID.randomUUID()}_${file.fileName}")
                FileOutputStream(assembledRawFile).use { fos ->
                    for ((idx, chunk) in chunks.withIndex()) {
                        val fileInfo = api.getFile(botToken, chunk.telegramFileId)
                        if (!fileInfo.isSuccessful || fileInfo.body()?.ok != true) {
                            return@withContext Result.failure(Exception("فشل جلب الجزء ${idx + 1} من تيليجرام"))
                        }
                        val tgFile = fileInfo.body()!!.result!!
                        val downloadUrl = TelegramClientProvider.getDirectDownloadUrl(botToken, tgFile.filePath ?: "")

                        val resp = api.downloadFileDirectly(downloadUrl)
                        if (!resp.isSuccessful || resp.body() == null) {
                            return@withContext Result.failure(Exception("فشل تنزيل الجزء ${idx + 1}"))
                        }

                        resp.body()!!.byteStream().use { stream ->
                            val buf = ByteArray(32 * 1024)
                            var r: Int
                            while (stream.read(buf).also { r = it } != -1) {
                                fos.write(buf, 0, r)
                            }
                        }

                        onProgress(
                            TransferProgress(
                                fileId = file.id,
                                fileName = file.originalFileName,
                                currentChunk = idx + 1,
                                totalChunks = chunks.size,
                                status = "تم تحميل الجزء (${idx + 1} / ${chunks.size})"
                            )
                        )
                    }
                }

                if (file.isEncrypted) {
                    if (decryptionPassword.isBlank()) {
                        assembledRawFile.delete()
                        return@withContext Result.failure(Exception("كلمة المرور مطلوبة لفك تشفير الملف المجمع."))
                    }
                    CryptoEngine.decryptFile(assembledRawFile, targetOutput, decryptionPassword)
                    assembledRawFile.delete()
                } else {
                    assembledRawFile.renameTo(targetOutput)
                }

                Result.success(targetOutput)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempEncryptedChunks.forEach { it.delete() }
            assembledRawFile?.delete()
        }
    }

    // -------------------------------------------------------------
    // FILE MANAGEMENT (DELETE, MOVE, RENAME)
    // -------------------------------------------------------------

    suspend fun deleteFile(file: DriveFileEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getAccountById(file.accountId)
            val botToken = getEffectiveBotToken(account)
            if (botToken.isNotBlank() && file.remoteMessageId > 0) {
                try {
                    val api = TelegramClientProvider.getApiService()
                    api.deleteMessage(botToken, file.channelId, file.remoteMessageId)
                } catch (ignored: Exception) {
                    // Telegram message might be already deleted or Bot has no delete permission
                }
            }

            if (file.isChunked) {
                val chunks = chunkDao.getChunksForFile(file.id)
                if (botToken.isNotBlank()) {
                    val api = TelegramClientProvider.getApiService()
                    chunks.forEach { chunk ->
                        try {
                            api.deleteMessage(botToken, file.channelId, chunk.remoteMessageId)
                        } catch (ignored: Exception) {}
                    }
                }
                chunkDao.deleteChunksByFileId(file.id)
            }

            fileDao.deleteFile(file)

            // Update vault total bytes
            val vault = vaultDao.getVaultByChannelId(file.channelId)
            if (vault != null) {
                val newFiles = (vault.totalFiles - 1).coerceAtLeast(0)
                val newBytes = (vault.totalBytes - file.sizeBytes).coerceAtLeast(0L)
                vaultDao.updateVault(vault.copy(totalFiles = newFiles, totalBytes = newBytes))
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFile(file: DriveFileEntity, newName: String) = withContext(Dispatchers.IO) {
        val updated = file.copy(
            originalFileName = newName,
            extension = newName.substringAfterLast('.', file.extension),
            updatedAt = System.currentTimeMillis()
        )
        fileDao.updateFile(updated)
    }

    suspend fun moveFileToFolder(file: DriveFileEntity, newFolderPath: String) = withContext(Dispatchers.IO) {
        val updated = file.copy(
            virtualFolderPath = newFolderPath,
            updatedAt = System.currentTimeMillis()
        )
        fileDao.updateFile(updated)
    }

    // -------------------------------------------------------------
    // PROXY & NETWORK SPEED TEST
    // -------------------------------------------------------------

    suspend fun testProxyLatency(proxy: ProxyConfigEntity): ProxyPingResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(proxy.host, proxy.port), 4000)
                val latency = System.currentTimeMillis() - start
                proxyDao.updateProxy(proxy.copy(lastPingMs = latency))
                ProxyPingResult(proxy.id, true, latency)
            }
        } catch (e: Exception) {
            ProxyPingResult(proxy.id, false, -1L, e.localizedMessage ?: "فشل الاتصال بالبروكسي")
        }
    }

    suspend fun addProxy(
        title: String,
        type: String,
        host: String,
        port: Int,
        username: String? = null,
        password: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val entity = ProxyConfigEntity(
            title = title,
            type = type,
            host = host.trim(),
            port = port,
            username = username?.trim(),
            password = password?.trim(),
            isActive = false,
            lastPingMs = -1L
        )
        proxyDao.insertProxy(entity)
    }

    suspend fun toggleProxyActive(proxyId: Long, active: Boolean) = withContext(Dispatchers.IO) {
        if (active) {
            proxyDao.setOnlyThisProxyActive(proxyId)
            val p = proxyDao.getAllProxies().firstOrNull()?.find { it.id == proxyId }
            if (p != null) {
                TelegramClientProvider.updateProxy(
                    TelegramClientProvider.ProxySetting(
                        type = p.type,
                        host = p.host,
                        port = p.port,
                        username = p.username,
                        password = p.password
                    )
                )
            }
        } else {
            proxyDao.deactivateAllProxies()
            TelegramClientProvider.updateProxy(null)
        }
    }

    suspend fun deleteProxy(proxy: ProxyConfigEntity) = withContext(Dispatchers.IO) {
        if (proxy.isActive) {
            TelegramClientProvider.updateProxy(null)
        }
        proxyDao.deleteProxy(proxy)
    }

    // -------------------------------------------------------------
    // SECURE SHARING LINKS
    // -------------------------------------------------------------

    suspend fun createShareLink(
        fileId: Long,
        password: String? = null,
        expiresInHours: Int? = null,
        maxDownloads: Int? = null
    ): Result<ShareLinkEntity> = withContext(Dispatchers.IO) {
        try {
            val code = UUID.randomUUID().toString().replace("-", "").take(12)
            val expiresAt = expiresInHours?.let { System.currentTimeMillis() + (it * 3600 * 1000L) }
            val pwdHash = if (!password.isNullOrBlank()) CryptoEngine.hashPassword(password, code) else null

            val link = ShareLinkEntity(
                fileId = fileId,
                shareCode = code,
                passwordHash = pwdHash,
                hasPassword = !password.isNullOrBlank(),
                expiresAt = expiresAt,
                maxDownloads = maxDownloads
            )
            val id = shareLinkDao.insertShareLink(link)
            Result.success(link.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Context.cacheStreamDir(): File {
        val dir = File(cacheDir, "teledrive_streams")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
