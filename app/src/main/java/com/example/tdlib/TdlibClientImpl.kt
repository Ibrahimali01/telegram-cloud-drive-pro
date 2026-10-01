package com.example.tdlib

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

class TdlibApiException(val code: Int, val rawMessage: String) :
    Exception(TdlibValidators.mapErrorToArabic(code, rawMessage))

interface TdlibClient {
    val accountId: Long
    val authState: StateFlow<TdApi.AuthorizationState?>
    val lastError: StateFlow<String?>
    val isOperating: StateFlow<Boolean>

    suspend fun setTdlibParameters(
        apiId: Int,
        apiHash: String,
        encryptionKey: ByteArray
    ): Result<Boolean>

    suspend fun setAuthenticationPhoneNumber(phoneNumber: String): Result<Boolean>
    suspend fun checkAuthenticationCode(code: String): Result<Boolean>
    suspend fun checkAuthenticationPassword(password: String): Result<Boolean>
    suspend fun checkAuthenticationBotToken(token: String): Result<Boolean>
    suspend fun requestQrCodeAuthentication(): Result<Boolean>
    suspend fun resendAuthenticationCode(): Result<Boolean>
    suspend fun getMe(): Result<TdApi.User>
    suspend fun logOut(): Result<Boolean>
    suspend fun close(): Result<Boolean>
    suspend fun destroy()
    fun clearError()
    suspend fun sendDocument(chatId: Long, filePath: String, caption: String = ""): Result<TdApi.Message>
    suspend fun resolveChatId(channelIdOrUsername: String): Result<Long>
    suspend fun verifyChannelOwnership(channelIdOrUsername: String): Result<TdlibChannelInfo>
    suspend fun getChatHistory(chatId: Long, fromMessageId: Long = 0L, limit: Int = 50): Result<TdApi.Messages>
}

data class TdlibChannelInfo(
    val chatId: Long,
    val title: String,
    val isOwnerOrAdmin: Boolean,
    val canPostMessages: Boolean,
    val statusDescription: String
)

/**
 * Real TDLib Client implementation backed by native TDLib (org.drinkless.tdlib.Client).
 * Strictly driven by updateAuthorizationState.
 * Never logs credentials, codes, passwords, or tokens.
 */
class TdlibClientImpl(
    private val context: Context,
    override val accountId: Long,
    val sessionDir: File
) : TdlibClient {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    override val authState: StateFlow<TdApi.AuthorizationState?> = _authState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    override val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    private var nativeClient: Client? = null

    init {
        if (!sessionDir.exists()) {
            sessionDir.mkdirs()
        }
        initNativeClient()
    }

    private fun initNativeClient() {
        try {
            nativeClient = Client.create(
                { obj ->
                    when (obj) {
                        is TdApi.UpdateAuthorizationState -> {
                            _authState.value = obj.authorizationState
                            // Clear transient error when state progresses to a fresh step
                            if (obj.authorizationState !is TdApi.AuthorizationStateClosed) {
                                _lastError.value = null
                            }
                        }
                    }
                },
                { updateException ->
                    // Uncaught update exception
                },
                { defaultException ->
                    // Uncaught default exception
                }
            )
        } catch (e: Throwable) {
            _lastError.value = "فشل تهيئة محرك TDLib الأصلي: ${e.message}"
        }
    }

    private suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): Result<T> =
        withContext(Dispatchers.IO) {
            val client = nativeClient ?: return@withContext Result.failure(
                IllegalStateException("محرك TDLib غير مهيأ")
            )
            _isOperating.value = true
            suspendCancellableCoroutine { continuation ->
                client.send(
                    function,
                    { result ->
                        _isOperating.value = false
                        if (result is TdApi.Error) {
                            val apiEx = TdlibApiException(result.code, result.message)
                            _lastError.value = apiEx.message
                            continuation.resume(Result.failure(apiEx))
                        } else {
                            _lastError.value = null
                            @Suppress("UNCHECKED_CAST")
                            continuation.resume(Result.success(result as T))
                        }
                    },
                    { exception ->
                        _isOperating.value = false
                        val ex = exception ?: Exception("خطأ أثناء الاتصال بمحرك TDLib")
                        _lastError.value = ex.localizedMessage ?: "فشل الاتصال"
                        continuation.resume(Result.failure(ex))
                    }
                )
            }
        }

    override suspend fun setTdlibParameters(
        apiId: Int,
        apiHash: String,
        encryptionKey: ByteArray
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val dbDir = File(sessionDir, "db").apply { mkdirs() }
        val filesDir = File(sessionDir, "files").apply { mkdirs() }

        val parameters = TdApi.SetTdlibParameters(
            /* useTestDc = */ false,
            /* databaseDirectory = */ dbDir.absolutePath,
            /* filesDirectory = */ filesDir.absolutePath,
            /* databaseEncryptionKey = */ encryptionKey,
            /* useFileDatabase = */ true,
            /* useChatInfoDatabase = */ true,
            /* useMessageDatabase = */ true,
            /* useSecretChats = */ false,
            /* apiId = */ apiId,
            /* apiHash = */ apiHash,
            /* systemLanguageCode = */ "ar",
            /* deviceModel = */ Build.MODEL ?: "Android Device",
            /* systemVersion = */ Build.VERSION.RELEASE ?: "14",
            /* applicationVersion = */ "1.0.0"
        )

        val result = send(parameters)
        if (result.isSuccess) {
            Result.success(true)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("فشل تعيين إعدادات TDLib"))
        }
    }

    override suspend fun setAuthenticationPhoneNumber(phoneNumber: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val clean = TdlibValidators.normalizePhone(phoneNumber)
            if (!TdlibValidators.isValidPhoneE164(clean)) {
                val err = "رقم الهاتف غير صالح. يجب أن يبدأ بـ (+) متبوعاً بالرقم الدولي (E.164)"
                _lastError.value = err
                return@withContext Result.failure(IllegalArgumentException(err))
            }
            val settings = TdApi.PhoneNumberAuthenticationSettings()
            val result = send(TdApi.SetAuthenticationPhoneNumber(clean, settings))
            if (result.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("فشل تعيين رقم الهاتف"))
            }
        }

    override suspend fun checkAuthenticationCode(code: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val clean = code.trim()
            if (clean.isEmpty() || !clean.all { it.isDigit() }) {
                val err = "رمز التحقق يجب أن يحتوي على أرقام فقط"
                _lastError.value = err
                return@withContext Result.failure(IllegalArgumentException(err))
            }
            val result = send(TdApi.CheckAuthenticationCode(clean))
            if (result.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("رمز التحقق غير صالح"))
            }
        }

    override suspend fun checkAuthenticationPassword(password: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            if (password.isEmpty()) {
                val err = "يرجى إدخال كلمة المرور بخطوتين (2FA)"
                _lastError.value = err
                return@withContext Result.failure(IllegalArgumentException(err))
            }
            val result = send(TdApi.CheckAuthenticationPassword(password))
            if (result.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("كلمة المرور غير صحيحة"))
            }
        }

    override suspend fun checkAuthenticationBotToken(token: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val clean = token.trim()
            if (!TdlibValidators.isValidBotToken(clean)) {
                val err = "تنسيق رمز البوت غير صالح. يجب أن يكون 123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ"
                _lastError.value = err
                return@withContext Result.failure(IllegalArgumentException(err))
            }
            val result = send(TdApi.CheckAuthenticationBotToken(clean))
            if (result.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("رمز البوت غير صالح"))
            }
        }

    override suspend fun requestQrCodeAuthentication(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val result = send(TdApi.RequestQrCodeAuthentication(longArrayOf()))
            if (result.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("فشل طلب تسجيل الدخول عبر QR"))
            }
        }

    override suspend fun resendAuthenticationCode(): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val result = send(TdApi.ResendAuthenticationCode())
            if (result.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("فشل إعادة إرسال رمز التحقق"))
            }
        }

    override suspend fun getMe(): Result<TdApi.User> = withContext(Dispatchers.IO) {
        send(TdApi.GetMe())
    }

    override suspend fun logOut(): Result<Boolean> = withContext(Dispatchers.IO) {
        val result = send(TdApi.LogOut())
        sessionDir.deleteRecursively()
        if (result.isSuccess) Result.success(true) else Result.failure(result.exceptionOrNull() ?: Exception("فشل تسجيل الخروج"))
    }

    override suspend fun close(): Result<Boolean> = withContext(Dispatchers.IO) {
        val result = send(TdApi.Close())
        if (result.isSuccess) Result.success(true) else Result.failure(result.exceptionOrNull() ?: Exception("فشل إغلاق الجلسة"))
    }

    override suspend fun destroy() = withContext(Dispatchers.IO) {
        try {
            send(TdApi.Destroy())
        } catch (_: Exception) {}
        nativeClient = null
        _authState.value = TdApi.AuthorizationStateClosed()
    }

    override fun clearError() {
        _lastError.value = null
    }

    override suspend fun sendDocument(
        chatId: Long,
        filePath: String,
        caption: String
    ): Result<TdApi.Message> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(filePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("ملف الرفع المحلي غير موجود: $filePath"))
            }

            val tdFilesDir = File(sessionDir, "files").apply { mkdirs() }
            val persistentFile = File(tdFilesDir, "up_${UUID.randomUUID()}_${sourceFile.name}")
            sourceFile.copyTo(persistentFile, overwrite = true)

            val inputFile = TdApi.InputFileLocal().apply {
                this.path = persistentFile.absolutePath
            }
            val req = TdApi.SendMessage().apply {
                this.chatId = chatId
                this.inputMessageContent = TdApi.InputMessageDocument().apply {
                    this.document = TdApi.InputDocument(inputFile, null, false)
                    this.caption = TdApi.FormattedText().apply {
                        this.text = caption
                    }
                }
            }
            send(req)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resolveChatId(channelIdOrUsername: String): Result<Long> = withContext(Dispatchers.IO) {
        val clean = channelIdOrUsername.trim()
            .removePrefix("https://t.me/")
            .removePrefix("http://t.me/")
            .removePrefix("t.me/")

        val digitsOnly = clean.removePrefix("-100").removePrefix("-").trim()
        val numericId = digitsOnly.toLongOrNull()

        if (numericId != null) {
            val fullChatId = if (clean.startsWith("-100")) clean.toLong() else "-100$numericId".toLong()
            val getFullRes = send(TdApi.GetChat(fullChatId))
            if (getFullRes.isSuccess) {
                return@withContext Result.success(getFullRes.getOrThrow().id)
            }
            val sgRes = send(TdApi.CreateSupergroupChat(numericId, false))
            if (sgRes.isSuccess) {
                return@withContext Result.success(sgRes.getOrThrow().id)
            }
            return@withContext Result.success(fullChatId)
        }

        val usernameToSearch = if (clean.startsWith("@")) clean.drop(1) else clean
        val searchRes = send(TdApi.SearchPublicChat(usernameToSearch))
        if (searchRes.isSuccess) {
            Result.success(searchRes.getOrThrow().id)
        } else {
            Result.failure(searchRes.exceptionOrNull() ?: Exception("لم يتم العثور على القناة في تيليجرام"))
        }
    }

    override suspend fun getChatHistory(chatId: Long, fromMessageId: Long, limit: Int): Result<TdApi.Messages> = withContext(Dispatchers.IO) {
        val req = TdApi.GetChatHistory().apply {
            this.chatId = chatId
            this.fromMessageId = fromMessageId
            this.offset = 0
            this.limit = limit
            this.onlyLocal = false
        }
        send(req)
    }

    override suspend fun verifyChannelOwnership(channelIdOrUsername: String): Result<TdlibChannelInfo> = withContext(Dispatchers.IO) {
        try {
            val clean = channelIdOrUsername.trim()
                .removePrefix("https://t.me/")
                .removePrefix("http://t.me/")
                .removePrefix("t.me/")
                .trim()

            if (clean.isBlank()) {
                return@withContext Result.failure(Exception("معرف أو رابط القناة فارغ"))
            }

            // 1. Get current logged in user ID
            val meRes = getMe()
            if (meRes.isFailure) {
                return@withContext Result.failure(Exception("جلسة المستخدم غير نشطة أو لم تكتمل المصادقة بعد."))
            }
            val myUserId = meRes.getOrThrow().id

            // 2. Resolve Chat by numeric ID, username, or direct handle
            var chat: TdApi.Chat? = null
            val digitsOnly = clean.removePrefix("-100").removePrefix("-").trim()
            val numericId = digitsOnly.toLongOrNull()

            if (numericId != null) {
                val fullChatId = if (clean.startsWith("-100")) clean.toLong() else "-100$numericId".toLong()
                
                // Try 1: GetChat with -100 prefix
                val getFullRes = send(TdApi.GetChat(fullChatId))
                if (getFullRes.isSuccess) {
                    chat = getFullRes.getOrThrow()
                } else {
                    // Try 2: CreateSupergroupChat with numeric ID
                    val sgRes = send(TdApi.CreateSupergroupChat(numericId, false))
                    if (sgRes.isSuccess) {
                        chat = sgRes.getOrThrow()
                    } else {
                        // Try 3: Direct ID
                        val directRes = send(TdApi.GetChat(clean.toLongOrNull() ?: numericId))
                        if (directRes.isSuccess) {
                            chat = directRes.getOrThrow()
                        }
                    }
                }
            } else {
                val username = clean.removePrefix("@")
                val searchRes = send(TdApi.SearchPublicChat(username))
                if (searchRes.isSuccess) {
                    chat = searchRes.getOrThrow()
                }
            }

            if (chat == null) {
                return@withContext Result.failure(
                    Exception("تعذر العثور على القناة ($clean) على تيليجرام. تأكد من أنك عضو أو منشئ للقناة أو أدخل معرفها العام (مثل @my_channel).")
                )
            }

            // 3. Query the user's membership and admin status in this chat
            val memberRes = send(TdApi.GetChatMember(chat.id, TdApi.MessageSenderUser(myUserId)))
            if (memberRes.isFailure) {
                return@withContext Result.failure(
                    Exception("أنت لست عضواً أو مشرفاً في القناة (${chat.title}). يجب أن تكون صاحب القناة أو مشرفاً فيها.")
                )
            }

            val member = memberRes.getOrThrow()
            when (val s = member.status) {
                is TdApi.ChatMemberStatusCreator -> {
                    Result.success(
                        TdlibChannelInfo(
                            chatId = chat.id,
                            title = chat.title,
                            isOwnerOrAdmin = true,
                            canPostMessages = true,
                            statusDescription = "أنت المالك والمنشئ للقناة (Owner) - لديك كامل الصلاحيات ✅"
                        )
                    )
                }
                is TdApi.ChatMemberStatusAdministrator -> {
                    val canPost = s.rights.canPostMessages
                    if (canPost) {
                        Result.success(
                            TdlibChannelInfo(
                                chatId = chat.id,
                                title = chat.title,
                                isOwnerOrAdmin = true,
                                canPostMessages = true,
                                statusDescription = "أنت مشرف (Admin) في القناة وتمتلك صلاحية نشر الرسائل ✅"
                            )
                        )
                    } else {
                        Result.failure(
                            Exception("أنت مشرف في القناة (${chat.title}) ولكن لا تملك صلاحية 'نشر الرسائل' (Post Messages). يرجى تفعيلها أولاً من إعدادات القناة في تيليجرام ⚠️")
                        )
                    }
                }
                is TdApi.ChatMemberStatusMember -> {
                    Result.failure(
                        Exception("أنت عضو عادي فقط في القناة (${chat.title}) ولست مشرفاً. لا يمكنك استخدام القناة للتخزين إلا إذا كنت مالكها أو مشرفاً فيها ❌")
                    )
                }
                else -> {
                    Result.failure(
                        Exception("حسابك غير مصرح له بالنشر في القناة (${chat.title}). يجب أن تكون مالكاً أو مشرفاً ❌")
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
