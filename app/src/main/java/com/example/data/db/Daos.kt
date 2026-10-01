package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TelegramAccountDao {
    @Query("SELECT * FROM telegram_accounts ORDER BY isPrimary DESC, createdAt DESC")
    fun getAllAccounts(): Flow<List<TelegramAccountEntity>>

    @Query("SELECT * FROM telegram_accounts ORDER BY isPrimary DESC, createdAt DESC")
    suspend fun getAllAccountsDirect(): List<TelegramAccountEntity>

    @Query("SELECT * FROM telegram_accounts ORDER BY isPrimary DESC, createdAt DESC LIMIT 1")
    fun getPrimaryAccount(): Flow<TelegramAccountEntity?>

    @Query("SELECT * FROM telegram_accounts ORDER BY isPrimary DESC, createdAt DESC LIMIT 1")
    suspend fun getPrimaryAccountDirect(): TelegramAccountEntity?

    @Query("SELECT * FROM telegram_accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): TelegramAccountEntity?

    @Query("SELECT * FROM telegram_accounts WHERE telegramUserId = :userId LIMIT 1")
    suspend fun getAccountByTelegramUserId(userId: Long): TelegramAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: TelegramAccountEntity): Long

    @Update
    suspend fun updateAccount(account: TelegramAccountEntity)

    @Query("UPDATE telegram_accounts SET isPrimary = 0 WHERE id != :primaryId")
    suspend fun clearOtherPrimaryAccounts(primaryId: Long)

    @Delete
    suspend fun deleteAccount(account: TelegramAccountEntity)
}

@Dao
interface DriveVaultDao {
    @Query("SELECT * FROM drive_vaults ORDER BY isDefault DESC, createdAt DESC")
    fun getAllVaults(): Flow<List<DriveVaultEntity>>

    @Query("SELECT * FROM drive_vaults WHERE accountId = :accountId ORDER BY createdAt DESC")
    fun getVaultsByAccount(accountId: Long): Flow<List<DriveVaultEntity>>

    @Query("SELECT * FROM drive_vaults WHERE accountId = :accountId ORDER BY createdAt DESC")
    suspend fun getVaultsByAccountDirect(accountId: Long): List<DriveVaultEntity>

    @Query("SELECT * FROM drive_vaults ORDER BY isDefault DESC, createdAt DESC LIMIT 1")
    fun getDefaultVault(): Flow<DriveVaultEntity?>

    @Query("SELECT * FROM drive_vaults ORDER BY isDefault DESC, createdAt DESC LIMIT 1")
    suspend fun getDefaultVaultDirect(): DriveVaultEntity?

    @Query("SELECT * FROM drive_vaults WHERE channelId = :channelId LIMIT 1")
    suspend fun getVaultByChannelId(channelId: String): DriveVaultEntity?

    @Query("SELECT * FROM drive_vaults WHERE id = :id")
    suspend fun getVaultById(id: Long): DriveVaultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVault(vault: DriveVaultEntity): Long

    @Update
    suspend fun updateVault(vault: DriveVaultEntity)

    @Query("UPDATE drive_vaults SET isDefault = 0 WHERE id != :defaultId")
    suspend fun clearOtherDefaultVaults(defaultId: Long)

    @Delete
    suspend fun deleteVault(vault: DriveVaultEntity)
}

@Dao
interface DriveFileDao {
    @Query("SELECT * FROM drive_files ORDER BY createdAt DESC")
    fun getAllFiles(): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE virtualFolderPath = :folderPath ORDER BY createdAt DESC")
    fun getFilesByFolder(folderPath: String): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE isEncrypted = 1 ORDER BY createdAt DESC")
    fun getEncryptedFiles(): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE mimeType LIKE 'image/%' ORDER BY createdAt DESC")
    fun getImageFiles(): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE mimeType LIKE 'video/%' ORDER BY createdAt DESC")
    fun getVideoFiles(): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE mimeType LIKE 'audio/%' ORDER BY createdAt DESC")
    fun getAudioFiles(): Flow<List<DriveFileEntity>>

    @Query("SELECT * FROM drive_files WHERE fileName LIKE '%' || :query || '%' OR originalFileName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchFiles(query: String): Flow<List<DriveFileEntity>>

    @Query("SELECT DISTINCT virtualFolderPath FROM drive_files")
    fun getAllFolders(): Flow<List<String>>

    @Query("SELECT * FROM drive_files WHERE id = :id")
    suspend fun getFileById(id: Long): DriveFileEntity?

    @Query("SELECT * FROM drive_files WHERE telegramFileId = :fileId OR remoteMessageId = :msgId LIMIT 1")
    suspend fun getFileByTelegramFileIdOrMessageId(fileId: String, msgId: Long): DriveFileEntity?

    @Query("SELECT * FROM drive_files")
    suspend fun getAllFilesDirect(): List<DriveFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: DriveFileEntity): Long

    @Update
    suspend fun updateFile(file: DriveFileEntity)

    @Delete
    suspend fun deleteFile(file: DriveFileEntity)

    @Query("DELETE FROM drive_files WHERE id = :id")
    suspend fun deleteFileById(id: Long)

    @Query("DELETE FROM drive_files WHERE accountId = :accountId")
    suspend fun deleteFilesByAccount(accountId: Long)

    @Query("DELETE FROM drive_files WHERE channelId = :channelId")
    suspend fun deleteFilesByChannel(channelId: String)

    @Query("SELECT SUM(sizeBytes) FROM drive_files")
    fun getTotalStorageUsed(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM drive_files")
    fun getTotalFileCount(): Flow<Int>
}

@Dao
interface FileChunkDao {
    @Query("SELECT * FROM file_chunks WHERE fileId = :fileId ORDER BY chunkIndex ASC")
    suspend fun getChunksForFile(fileId: Long): List<FileChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: FileChunkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<FileChunkEntity>)

    @Query("DELETE FROM file_chunks WHERE fileId = :fileId")
    suspend fun deleteChunksByFileId(fileId: Long)
}

@Dao
interface ProxyConfigDao {
    @Query("SELECT * FROM proxy_configs ORDER BY isActive DESC, id ASC")
    fun getAllProxies(): Flow<List<ProxyConfigEntity>>

    @Query("SELECT * FROM proxy_configs WHERE isActive = 1 LIMIT 1")
    fun getActiveProxy(): Flow<ProxyConfigEntity?>

    @Query("SELECT * FROM proxy_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProxyDirect(): ProxyConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxy(proxy: ProxyConfigEntity): Long

    @Update
    suspend fun updateProxy(proxy: ProxyConfigEntity)

    @Query("UPDATE proxy_configs SET isActive = 0 WHERE id != :activeId")
    suspend fun setOnlyThisProxyActive(activeId: Long)

    @Query("UPDATE proxy_configs SET isActive = 0")
    suspend fun deactivateAllProxies()

    @Delete
    suspend fun deleteProxy(proxy: ProxyConfigEntity)
}

@Dao
interface ShareLinkDao {
    @Query("SELECT * FROM share_links WHERE fileId = :fileId AND isRevoked = 0")
    fun getActiveLinksForFile(fileId: Long): Flow<List<ShareLinkEntity>>

    @Query("SELECT * FROM share_links WHERE shareCode = :code LIMIT 1")
    suspend fun getLinkByCode(code: String): ShareLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShareLink(link: ShareLinkEntity): Long

    @Update
    suspend fun updateShareLink(link: ShareLinkEntity)

    @Query("UPDATE share_links SET downloadCount = downloadCount + 1 WHERE id = :id")
    suspend fun incrementDownloadCount(id: Long)
}
