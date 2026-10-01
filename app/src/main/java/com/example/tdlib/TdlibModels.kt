package com.example.tdlib

import org.drinkless.tdlib.TdApi

/**
 * TDLib Data Models, validation rules, and Arabic localized errors.
 */

data class TdlibUserSummary(
    val id: Long,
    val firstName: String,
    val lastName: String = "",
    val username: String = "",
    val phoneNumber: String = "",
    val isBot: Boolean = false,
    val isPremium: Boolean = false
)

object TdlibValidators {
    private val E164_REGEX = Regex("^\\+[1-9]\\d{6,14}$")
    private val API_HASH_REGEX = Regex("^[0-9a-fA-F]{32}$")
    private val BOT_TOKEN_REGEX = Regex("^\\d{6,10}:[A-Za-z0-9_-]{35}$")

    fun isValidApiId(apiId: String): Boolean {
        val trimmed = apiId.trim()
        if (trimmed.isEmpty() || !trimmed.all { it.isDigit() }) return false
        val num = trimmed.toLongOrNull() ?: return false
        return num in 1..Int.MAX_VALUE
    }

    fun isDesktopApiId(apiId: String): Boolean {
        return apiId.trim() == "2040"
    }

    fun isValidApiHash(apiHash: String): Boolean {
        return apiHash.trim().matches(API_HASH_REGEX)
    }

    fun normalizePhone(phone: String): String {
        return phone.trim().replace(" ", "").replace("-", "")
    }

    fun isValidPhoneE164(phone: String): Boolean {
        return normalizePhone(phone).matches(E164_REGEX)
    }

    fun isValidBotToken(token: String): Boolean {
        return token.trim().matches(BOT_TOKEN_REGEX)
    }

    fun isTelegramLink(text: String): Boolean {
        val t = text.trim().lowercase()
        return t.contains("t.me/") || t.contains("telegram.me/")
    }

    fun extractFloodWaitSeconds(message: String): Int? {
        val match = Regex("FLOOD_WAIT_(\\d+)").find(message)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    fun getRequiredCodeLength(codeInfo: TdApi.AuthenticationCodeInfo?): Int {
        if (codeInfo == null) return 5
        return when (val type = codeInfo.type) {
            is TdApi.AuthenticationCodeTypeTelegramMessage -> type.length.coerceAtLeast(1)
            is TdApi.AuthenticationCodeTypeSms -> type.length.coerceAtLeast(1)
            is TdApi.AuthenticationCodeTypeCall -> type.length.coerceAtLeast(1)
            is TdApi.AuthenticationCodeTypeMissedCall -> type.length.coerceAtLeast(1)
            else -> 5
        }
    }

    fun mapErrorToArabic(code: Int, message: String): String {
        val upper = message.uppercase()
        return when {
            upper.contains("PHONE_NUMBER_INVALID") ->
                "رقم الهاتف غير صالح أو غير مسجل في تيليجرام."
            upper.contains("PHONE_NUMBER_FLOOD") ->
                "تم تجاوز عدد المحاولات المسموح به لهذا الرقم (Flood Wait). يرجى المحاولة لاحقاً."
            upper.contains("PHONE_NUMBER_BANNED") ->
                "هذا الرقم محظور نهائياً من تيليجرام (Banned)."
            upper.contains("PHONE_NUMBER_UNOCCUPIED") ->
                "هذا الرقم ليس لديه حساب مسجل في تيليجرام."
            upper.contains("PHONE_CODE_INVALID") ->
                "رمز التحقق غير صحيح، يرجى التأكد من الرمز وإعادة المحاولة."
            upper.contains("PHONE_CODE_EXPIRED") ->
                "انتهت صلاحية رمز التحقق، يرجى طلب رمز جديد."
            upper.contains("PASSWORD_HASH_INVALID") ->
                "كلمة المرور بخطوتين (2FA) غير صحيحة، يرجى المحاولة مجدداً."
            upper.contains("ACCESS_TOKEN_INVALID") || upper.contains("BOT_TOKEN_INVALID") ->
                "رمز البوت (Bot Token) غير صالح أو تم إلغاؤه من @BotFather."
            upper.contains("API_ID_INVALID") ->
                "معرف API ID غير صالح أو غير موجود في خوادم تيليجرام."
            upper.contains("API_HASH_INVALID") ->
                "رمز API Hash غير مطابق لـ API ID المدخل."
            upper.contains("FLOOD_WAIT") -> {
                val sec = extractFloodWaitSeconds(message)
                if (sec != null) "يرجى الانتظار $sec ثانية قبل المحاولة مجدداً بسبب كثرة الطلبات (Flood Wait)."
                else "يرجى الانتظار قليلاً قبل المحاولة مجدداً (Flood Wait)."
            }
            upper.contains("CONNECTION_LOST") || upper.contains("WAITING_FOR_NETWORK") ->
                "تعذر الاتصال بخوادم تيليجرام، تحقق من اتصال الإنترنت أو استخدم بروكسي."
            else -> message.ifBlank { "حدث خطأ من TDLib (كود $code)" }
        }
    }
}
