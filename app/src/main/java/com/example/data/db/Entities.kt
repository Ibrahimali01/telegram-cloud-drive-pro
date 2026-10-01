package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telegram_accounts",
    indices = [Index(value = ["telegramUserId"])]
)
data class TelegramAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val telegramUserId: Long = 0L,
    val accountName: String,
    val username: String,
    val botToken: String,
    val apiId: String? = null,
    val apiHash: String? = null,
    val phoneNumber: String? = null,
    val isBot: Boolean = true,
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "drive_vaults",
    foreignKeys = [
        ForeignKey(
            entity = TelegramAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId"), Index("channelId")]
)
data class DriveVaultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val channelTitle: String,
    val accountId: Long,
    val isEncrypted: Boolean = false,
    val salt: String = "",
    val isDefault: Boolean = false,
    val totalFiles: Int = 0,
    val totalBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "drive_files",
    indices = [
        Index("accountId"),
        Index("channelId"),
        Index("virtualFolderPath"),
        Index("isEncrypted")
    ]
)
data class DriveFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteMessageId: Long = 0L,
    val telegramFileId: String = "",
    val telegramFileUniqueId: String = "",
    val fileName: String,
    val originalFileName: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val channelId: String,
    val accountId: Long,
    val virtualFolderPath: String = "/",
    val isEncrypted: Boolean = false,
    val encryptionAlgorithm: String = "AES-256-GCM",
    val sha256Checksum: String = "",
    val isChunked: Boolean = false,
    val totalChunks: Int = 1,
    val uploadStatus: String = "COMPLETED", // "PENDING", "UPLOADING", "COMPLETED", "FAILED"
    val downloadUrl: String? = null,
    val localCachePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "file_chunks",
    foreignKeys = [
        ForeignKey(
            entity = DriveFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("fileId"), Index("chunkIndex")]
)
data class FileChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,
    val chunkIndex: Int,
    val totalChunks: Int,
    val remoteMessageId: Long,
    val telegramFileId: String,
    val chunkSizeBytes: Long,
    val sha256: String = ""
)

@Entity(tableName = "proxy_configs")
data class ProxyConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String = "SOCKS5", // "SOCKS5", "HTTP", "DIRECT"
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val isActive: Boolean = false,
    val lastPingMs: Long = -1L
)

@Entity(
    tableName = "share_links",
    foreignKeys = [
        ForeignKey(
            entity = DriveFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("fileId"), Index("shareCode", unique = true)]
)
data class ShareLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,
    val shareCode: String,
    val passwordHash: String? = null,
    val hasPassword: Boolean = false,
    val expiresAt: Long? = null,
    val maxDownloads: Int? = null,
    val downloadCount: Int = 0,
    val isRevoked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
