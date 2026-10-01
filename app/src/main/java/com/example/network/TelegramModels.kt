package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TelegramApiResponse<T>(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "result") val result: T?,
    @Json(name = "description") val description: String? = null,
    @Json(name = "error_code") val errorCode: Int? = null
)

@JsonClass(generateAdapter = true)
data class TelegramUser(
    @Json(name = "id") val id: Long,
    @Json(name = "is_bot") val isBot: Boolean,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "can_join_groups") val canJoinGroups: Boolean? = null,
    @Json(name = "can_read_all_group_messages") val canReadAllGroupMessages: Boolean? = null,
    @Json(name = "supports_inline_queries") val supportsInlineQueries: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class TelegramChat(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String? = null,
    @Json(name = "type") val type: String, // channel, supergroup, group, private
    @Json(name = "username") val username: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "invite_link") val inviteLink: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramMessage(
    @Json(name = "message_id") val messageId: Long,
    @Json(name = "date") val date: Long,
    @Json(name = "chat") val chat: TelegramChat,
    @Json(name = "document") val document: TelegramDocument? = null,
    @Json(name = "video") val video: TelegramVideo? = null,
    @Json(name = "audio") val audio: TelegramAudio? = null,
    @Json(name = "photo") val photo: List<TelegramPhotoSize>? = null,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramDocument(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "file_size") val fileSize: Long? = null
)

@JsonClass(generateAdapter = true)
data class TelegramVideo(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "file_size") val fileSize: Long? = null
)

@JsonClass(generateAdapter = true)
data class TelegramAudio(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "performer") val performer: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "file_size") val fileSize: Long? = null
)

@JsonClass(generateAdapter = true)
data class TelegramPhotoSize(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "width") val width: Int,
    @Json(name = "height") val height: Int,
    @Json(name = "file_size") val fileSize: Long? = null
)

@JsonClass(generateAdapter = true)
data class TelegramChatMember(
    @Json(name = "status") val status: String, // creator, administrator, member, restricted, left, kicked
    @Json(name = "user") val user: TelegramUser? = null,
    @Json(name = "can_post_messages") val canPostMessages: Boolean? = null,
    @Json(name = "can_edit_messages") val canEditMessages: Boolean? = null,
    @Json(name = "can_delete_messages") val canDeleteMessages: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class TelegramFile(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "file_size") val fileSize: Long? = null,
    @Json(name = "file_path") val filePath: String? = null
)
