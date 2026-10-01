package com.example

import com.example.crypto.CryptoEngine
import com.example.tdlib.TdlibValidators
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testCryptoEngineEncryptionAndDecryption() {
        val originalText = "Telegram Cloud Drive with Zero-Knowledge E2EE test payload! 12345"
        val password = "SuperSecretMasterKey@2026"

        val originalBytes = originalText.toByteArray(Charsets.UTF_8)
        val encryptedEnvelope = CryptoEngine.encryptBytes(originalBytes, password)

        assertTrue(CryptoEngine.isEncryptedPayload(encryptedEnvelope))
        assertFalse(originalBytes.contentEquals(encryptedEnvelope))

        val decryptedBytes = CryptoEngine.decryptBytes(encryptedEnvelope, password)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)

        assertEquals(originalText, decryptedText)
    }

    @Test
    fun testSha256Checksum() {
        val data = "Hello TeleDrive".toByteArray(Charsets.UTF_8)
        val sha = CryptoEngine.calculateSha256(data)
        assertEquals(64, sha.length)
    }

    @Test
    fun testTdlibValidationRules() {
        // API ID
        assertTrue(TdlibValidators.isValidApiId("1234567"))
        assertTrue(TdlibValidators.isDesktopApiId("2040"))
        assertFalse(TdlibValidators.isValidApiId("0"))
        assertFalse(TdlibValidators.isValidApiId("-5"))
        assertFalse(TdlibValidators.isValidApiId("abc"))

        // API Hash
        assertTrue(TdlibValidators.isValidApiHash("b18441a1ff607e10a989891a5462e627"))
        assertFalse(TdlibValidators.isValidApiHash("short"))
        assertFalse(TdlibValidators.isValidApiHash("b18441a1ff607e10a989891a5462e62g")) // 'g' is non-hex

        // Phone E.164
        assertTrue(TdlibValidators.isValidPhoneE164("+966501234567"))
        assertTrue(TdlibValidators.isValidPhoneE164("+201012345678"))
        assertFalse(TdlibValidators.isValidPhoneE164("0501234567")) // missing '+'
        assertFalse(TdlibValidators.isValidPhoneE164("+0123456")) // starting with 0

        // Bot Token
        assertTrue(TdlibValidators.isValidBotToken("123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ123456789"))
        assertFalse(TdlibValidators.isValidBotToken("invalid:token"))

        // Session String detection
        assertTrue(TdlibValidators.isTelegramLink("https://t.me/mychannel"))
        assertFalse(TdlibValidators.isTelegramLink("1ApWapzMBu4..."))
    }

    @Test
    fun testTdApiAuthStateObjects() {
        val codeInfo = TdApi.AuthenticationCodeInfo(
            "+12025550199",
            TdApi.AuthenticationCodeTypeTelegramMessage(5),
            null,
            60
        )
        val waitCode = TdApi.AuthorizationStateWaitCode(codeInfo)
        assertEquals(5, TdlibValidators.getRequiredCodeLength(waitCode.codeInfo))

        val waitPassword = TdApi.AuthorizationStateWaitPassword("hint_example", false, false, "")
        assertEquals("hint_example", waitPassword.passwordHint)
    }
}
