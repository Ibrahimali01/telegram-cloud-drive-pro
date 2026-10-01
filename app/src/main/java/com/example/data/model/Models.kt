package com.example.data.model

import java.io.File

enum class FileCategory(val titleAr: String, val titleEn: String) {
    ALL("الكل", "All"),
    DOCUMENTS("المستندات", "Documents"),
    MEDIA("الصور والفيديو", "Media"),
    AUDIO("الصوتيات", "Audio"),
    ENCRYPTED("المخزن المشفر 🔒", "Encrypted Vault"),
    ARCHIVES("الأرشيف والمضغوط", "Archives")
}

enum class SortOrder(val titleAr: String) {
    NEWEST("الأحدث أولاً"),
    OLDEST("الأقدم أولاً"),
    SIZE_DESC("الأكبر حجماً"),
    SIZE_ASC("الأصغر حجماً"),
    NAME_AZ("الاسم (أ - ي)")
}

data class StorageStats(
    val totalBytes: Long = 0L,
    val totalFiles: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val audioCount: Int = 0,
    val docCount: Int = 0,
    val encryptedCount: Int = 0,
    val archiveCount: Int = 0
)

data class TransferProgress(
    val fileId: Long = 0L,
    val fileName: String = "",
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val currentChunk: Int = 1,
    val totalChunks: Int = 1,
    val isEncrypting: Boolean = false,
    val isDecrypting: Boolean = false,
    val status: String = "جارٍ المعالجة..."
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedSpeed: String
        get() = when {
            speedBytesPerSec > 1024 * 1024 -> "%.2f MB/s".format(speedBytesPerSec / (1024.0 * 1024.0))
            speedBytesPerSec > 1024 -> "%.1f KB/s".format(speedBytesPerSec / 1024.0)
            else -> "$speedBytesPerSec B/s"
        }
}

data class ProxyPingResult(
    val proxyId: Long,
    val success: Boolean,
    val latencyMs: Long,
    val errorMessage: String? = null
)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return "%.2f %s".format(bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
