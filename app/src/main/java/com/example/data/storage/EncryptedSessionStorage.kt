package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * EncryptedSessionStorage:
 * Uses AndroidX Security EncryptedSharedPreferences backed by Android KeyStore (AES256-GCM)
 * to securely persist Telegram API credentials, TDLib session tokens, and multi-account state
 * across app restarts.
 */
class EncryptedSessionStorage(private val context: Context) {

    private val TAG = "EncryptedSessionStorage"
    private val PREFS_FILE = "teledrive_secure_vault_prefs"

    private val sharedPreferences: SharedPreferences by lazy {
        initEncryptedPrefs()
    }

    private val _savedApiId = MutableStateFlow<String>("")
    val savedApiId: StateFlow<String> = _savedApiId.asStateFlow()

    private val _savedApiHash = MutableStateFlow<String>("")
    val savedApiHash: StateFlow<String> = _savedApiHash.asStateFlow()

    private val _savedAppTitle = MutableStateFlow<String>("")
    val savedAppTitle: StateFlow<String> = _savedAppTitle.asStateFlow()

    init {
        loadSavedCredentials()
    }

    private fun initEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "Hardware KeyStore failed, falling back to standard sandbox storage: ${e.message}")
            context.getSharedPreferences("${PREFS_FILE}_fallback", Context.MODE_PRIVATE)
        }
    }

    private fun loadSavedCredentials() {
        val apiId = sharedPreferences.getString(KEY_API_ID, "2040") ?: "2040"
        val apiHash = sharedPreferences.getString(KEY_API_HASH, "b18441a1ff607e10a989891a5462e627") ?: "b18441a1ff607e10a989891a5462e627"
        val appTitle = sharedPreferences.getString(KEY_APP_TITLE, "TeleDrive Android") ?: "TeleDrive Android"

        _savedApiId.value = apiId
        _savedApiHash.value = apiHash
        _savedAppTitle.value = appTitle
    }

    // -------------------------------------------------------------
    // API CREDENTIALS (my.telegram.org)
    // -------------------------------------------------------------

    fun saveApiCredentials(apiId: String, apiHash: String, appTitle: String = "TeleDrive App") {
        sharedPreferences.edit()
            .putString(KEY_API_ID, apiId.trim())
            .putString(KEY_API_HASH, apiHash.trim())
            .putString(KEY_APP_TITLE, appTitle.trim())
            .apply()

        _savedApiId.value = apiId.trim()
        _savedApiHash.value = apiHash.trim()
        _savedAppTitle.value = appTitle.trim()
    }

    fun getApiId(): String = _savedApiId.value.ifBlank { "2040" }
    fun getApiHash(): String = _savedApiHash.value.ifBlank { "b18441a1ff607e10a989891a5462e627" }
    fun getAppTitle(): String = _savedAppTitle.value

    // -------------------------------------------------------------
    // MULTI-ACCOUNT SESSIONS & TOKENS PERSISTENCE
    // -------------------------------------------------------------

    fun saveAccountSession(
        accountId: Long,
        phoneNumber: String,
        username: String,
        firstName: String,
        botToken: String = "",
        sessionEncryptionKeyHex: String = "",
        isBot: Boolean = false
    ) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_PREFIX_PHONE + accountId, phoneNumber)
        editor.putString(KEY_PREFIX_USERNAME + accountId, username)
        editor.putString(KEY_PREFIX_FIRST_NAME + accountId, firstName)
        editor.putString(KEY_PREFIX_BOT_TOKEN + accountId, botToken)
        editor.putString(KEY_PREFIX_SESSION_KEY + accountId, sessionEncryptionKeyHex)
        editor.putBoolean(KEY_PREFIX_IS_BOT + accountId, isBot)
        editor.putLong(KEY_PREFIX_LAST_SYNC + accountId, System.currentTimeMillis())

        // Add to accounts set
        val existingAccounts = getStoredAccountIds().toMutableSet()
        existingAccounts.add(accountId.toString())
        editor.putStringSet(KEY_STORED_ACCOUNT_IDS, existingAccounts)

        editor.apply()
    }

    fun getStoredAccountIds(): Set<String> {
        return sharedPreferences.getStringSet(KEY_STORED_ACCOUNT_IDS, emptySet()) ?: emptySet()
    }

    fun getAccountBotToken(accountId: Long): String {
        return sharedPreferences.getString(KEY_PREFIX_BOT_TOKEN + accountId, "") ?: ""
    }

    fun getAccountSessionKey(accountId: Long): String {
        return sharedPreferences.getString(KEY_PREFIX_SESSION_KEY + accountId, "") ?: ""
    }

    fun removeAccountSession(accountId: Long) {
        val editor = sharedPreferences.edit()
        editor.remove(KEY_PREFIX_PHONE + accountId)
        editor.remove(KEY_PREFIX_USERNAME + accountId)
        editor.remove(KEY_PREFIX_FIRST_NAME + accountId)
        editor.remove(KEY_PREFIX_BOT_TOKEN + accountId)
        editor.remove(KEY_PREFIX_SESSION_KEY + accountId)
        editor.remove(KEY_PREFIX_IS_BOT + accountId)
        editor.remove(KEY_PREFIX_LAST_SYNC + accountId)

        val existingAccounts = getStoredAccountIds().toMutableSet()
        existingAccounts.remove(accountId.toString())
        editor.putStringSet(KEY_STORED_ACCOUNT_IDS, existingAccounts)

        editor.apply()
    }

    // -------------------------------------------------------------
    // ACTIVE ACCOUNT & MASTER PASSWORD
    // -------------------------------------------------------------

    fun saveActiveAccountId(accountId: Long) {
        sharedPreferences.edit().putLong(KEY_ACTIVE_ACCOUNT_ID, accountId).apply()
    }

    fun getActiveAccountId(): Long {
        return sharedPreferences.getLong(KEY_ACTIVE_ACCOUNT_ID, -1L)
    }

    fun saveMasterPasswordHash(hash: String) {
        sharedPreferences.edit().putString(KEY_MASTER_PASSWORD_HASH, hash).apply()
    }

    fun getMasterPasswordHash(): String? {
        return sharedPreferences.getString(KEY_MASTER_PASSWORD_HASH, null)
    }

    fun clearAllSecureData() {
        sharedPreferences.edit().clear().apply()
        loadSavedCredentials()
    }

    companion object {
        private const val KEY_API_ID = "telegram_api_id"
        private const val KEY_API_HASH = "telegram_api_hash"
        private const val KEY_APP_TITLE = "telegram_app_title"

        private const val KEY_STORED_ACCOUNT_IDS = "stored_account_ids_set"
        private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        private const val KEY_MASTER_PASSWORD_HASH = "master_password_hash"

        private const val KEY_PREFIX_PHONE = "acc_phone_"
        private const val KEY_PREFIX_USERNAME = "acc_user_"
        private const val KEY_PREFIX_FIRST_NAME = "acc_name_"
        private const val KEY_PREFIX_BOT_TOKEN = "acc_token_"
        private const val KEY_PREFIX_SESSION_KEY = "acc_key_"
        private const val KEY_PREFIX_IS_BOT = "acc_is_bot_"
        private const val KEY_PREFIX_LAST_SYNC = "acc_sync_"
    }
}
