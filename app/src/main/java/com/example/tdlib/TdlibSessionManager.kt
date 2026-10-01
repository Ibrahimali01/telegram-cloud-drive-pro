package com.example.tdlib

import android.content.Context
import com.example.crypto.CryptoEngine
import com.example.crypto.SecureCredentialsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * TdlibSessionManager:
 * Manages isolated TDLib client instances and sandboxed database directories per account.
 * Credentials and database encryption keys are stored securely via SecureCredentialsStorage.
 * UI states are strictly driven by updateAuthorizationState.
 */
class TdlibSessionManager(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val secureStorage = SecureCredentialsStorage.getInstance(context)
    val baseSessionsDir = File(context.filesDir, "tdlib_sessions")

    private val activeClients = ConcurrentHashMap<Long, TdlibClient>()

    private val _currentActiveAccountId = MutableStateFlow<Long?>(null)
    val currentActiveAccountId: StateFlow<Long?> = _currentActiveAccountId.asStateFlow()

    private val _activeClientAuthState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val activeClientAuthState: StateFlow<TdApi.AuthorizationState?> = _activeClientAuthState.asStateFlow()

    init {
        if (!baseSessionsDir.exists()) {
            baseSessionsDir.mkdirs()
        }
    }

    fun getOrCreateClient(accountId: Long): TdlibClient {
        return activeClients.computeIfAbsent(accountId) { id ->
            val sessionDir = File(baseSessionsDir, "acc_$id")
            val client = TdlibClientImpl(context, id, sessionDir)

            scope.launch {
                client.authState.collect { state ->
                    if (_currentActiveAccountId.value == id) {
                        _activeClientAuthState.value = state
                    }
                }
            }
            client
        }
    }

    fun getClient(accountId: Long): TdlibClient? = activeClients[accountId]

    fun getAllActiveClients(): Collection<TdlibClient> = activeClients.values

    fun transferClientToAccount(tempSessionId: Long, newAccountId: Long): TdlibClient? {
        val client = activeClients.remove(tempSessionId) ?: return null
        activeClients[newAccountId] = client
        _currentActiveAccountId.value = newAccountId
        _activeClientAuthState.value = client.authState.value
        return client
    }

    suspend fun restoreSession(accountId: Long, apiId: Int, apiHash: String): TdlibClient? = withContext(Dispatchers.IO) {
        val existing = activeClients[accountId]
        if (existing != null && existing.authState.value is TdApi.AuthorizationStateReady) {
            return@withContext existing
        }

        // Check if any client is already authenticated and ready
        val anyReady = activeClients.values.firstOrNull { it.authState.value is TdApi.AuthorizationStateReady }
        if (anyReady != null) {
            activeClients[accountId] = anyReady
            _currentActiveAccountId.value = accountId
            _activeClientAuthState.value = anyReady.authState.value
            return@withContext anyReady
        }

        val sessionDir = File(baseSessionsDir, "acc_$accountId").takeIf { it.exists() }
            ?: baseSessionsDir.listFiles()?.firstOrNull { it.isDirectory && (it.name.startsWith("acc_") || it.name.startsWith("temp_")) }
            ?: return@withContext null

        val encKey = secureStorage.getDatabaseEncryptionKey(accountId)
            ?: CryptoEngine.hashPassword(apiHash, "tdlib_salt_$accountId").toByteArray().also {
                secureStorage.saveDatabaseEncryptionKey(accountId, it)
            }

        val client = TdlibClientImpl(context, accountId, sessionDir)
        activeClients[accountId] = client
        _currentActiveAccountId.value = accountId

        scope.launch {
            client.authState.collect { state ->
                if (_currentActiveAccountId.value == accountId) {
                    _activeClientAuthState.value = state
                }
            }
        }

        try {
            client.setTdlibParameters(apiId, apiHash, encKey)
        } catch (_: Exception) {}

        client
    }

    fun setActiveAccount(accountId: Long) {
        _currentActiveAccountId.value = accountId
        val client = getOrCreateClient(accountId)
        _activeClientAuthState.value = client.authState.value
    }

    suspend fun startNewAuthentication(
        tempSessionId: Long,
        apiId: Int,
        apiHash: String
    ): Result<TdlibClient> = withContext(Dispatchers.IO) {
        try {
            // Clean up any previous temp session
            cancelAuthentication(tempSessionId)

            val sessionDir = File(baseSessionsDir, "temp_$tempSessionId")
            sessionDir.mkdirs()

            val client = TdlibClientImpl(context, tempSessionId, sessionDir)
            activeClients[tempSessionId] = client
            _currentActiveAccountId.value = tempSessionId

            scope.launch {
                client.authState.collect { state ->
                    if (_currentActiveAccountId.value == tempSessionId) {
                        _activeClientAuthState.value = state
                    }
                }
            }

            // Derive database encryption key securely
            val encryptionKey = secureStorage.getDatabaseEncryptionKey(tempSessionId)
                ?: CryptoEngine.hashPassword(apiHash, "tdlib_salt_$tempSessionId").toByteArray().also {
                    secureStorage.saveDatabaseEncryptionKey(tempSessionId, it)
                }

            val initRes = client.setTdlibParameters(apiId, apiHash, encryptionKey)
            if (initRes.isFailure) {
                return@withContext Result.failure(initRes.exceptionOrNull() ?: Exception("فشل تعيين معاملات TDLib"))
            }

            Result.success(client)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelAuthentication(tempSessionId: Long) = withContext(Dispatchers.IO) {
        val client = activeClients.remove(tempSessionId)
        if (client != null) {
            try {
                client.destroy()
            } catch (_: Exception) {}
        }
        val tempDir = File(baseSessionsDir, "temp_$tempSessionId")
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        secureStorage.removeAccountCredentials(tempSessionId)
        if (_currentActiveAccountId.value == tempSessionId) {
            _currentActiveAccountId.value = activeClients.keys.firstOrNull()
            _activeClientAuthState.value = _currentActiveAccountId.value?.let { activeClients[it]?.authState?.value }
        }
    }

    suspend fun logOutAndDestroy(accountId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        val client = activeClients[accountId]
        val result = client?.logOut() ?: Result.success(true)
        client?.destroy()
        activeClients.remove(accountId)

        val sessionDir = File(baseSessionsDir, "acc_$accountId")
        if (sessionDir.exists()) {
            sessionDir.deleteRecursively()
        }
        secureStorage.removeAccountCredentials(accountId)

        if (_currentActiveAccountId.value == accountId) {
            _currentActiveAccountId.value = activeClients.keys.firstOrNull()
            _activeClientAuthState.value = _currentActiveAccountId.value?.let { activeClients[it]?.authState?.value }
        }
        result
    }
}
