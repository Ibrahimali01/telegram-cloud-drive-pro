package com.example.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface TelegramApiService {

    @GET("/bot{token}/getMe")
    suspend fun getMe(
        @Path(value = "token", encoded = true) token: String
    ): Response<TelegramApiResponse<TelegramUser>>

    @GET("/bot{token}/getChat")
    suspend fun getChat(
        @Path(value = "token", encoded = true) token: String,
        @Query("chat_id") chatId: String
    ): Response<TelegramApiResponse<TelegramChat>>

    @GET("/bot{token}/getChatMember")
    suspend fun getChatMember(
        @Path(value = "token", encoded = true) token: String,
        @Query("chat_id") chatId: String,
        @Query("user_id") userId: Long
    ): Response<TelegramApiResponse<TelegramChatMember>>

    @GET("/bot{token}/getFile")
    suspend fun getFile(
        @Path(value = "token", encoded = true) token: String,
        @Query("file_id") fileId: String
    ): Response<TelegramApiResponse<TelegramFile>>

    @Multipart
    @POST("/bot{token}/sendDocument")
    suspend fun sendDocument(
        @Path(value = "token", encoded = true) token: String,
        @Query("chat_id") chatId: String,
        @Part document: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null
    ): Response<TelegramApiResponse<TelegramMessage>>

    @Multipart
    @POST("/bot{token}/sendVideo")
    suspend fun sendVideo(
        @Path(value = "token", encoded = true) token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part video: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null,
        @Part("supports_streaming") supportsStreaming: RequestBody? = null
    ): Response<TelegramApiResponse<TelegramMessage>>

    @Multipart
    @POST("/bot{token}/sendAudio")
    suspend fun sendAudio(
        @Path(value = "token", encoded = true) token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part audio: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null
    ): Response<TelegramApiResponse<TelegramMessage>>

    @Multipart
    @POST("/bot{token}/sendPhoto")
    suspend fun sendPhoto(
        @Path(value = "token", encoded = true) token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part photo: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null
    ): Response<TelegramApiResponse<TelegramMessage>>

    @POST("/bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path(value = "token", encoded = true) token: String,
        @Query("chat_id") chatId: String,
        @Query("text") text: String
    ): Response<TelegramApiResponse<TelegramMessage>>

    @POST("/bot{token}/deleteMessage")
    suspend fun deleteMessage(
        @Path(value = "token", encoded = true) token: String,
        @Query("chat_id") chatId: String,
        @Query("message_id") messageId: Long
    ): Response<TelegramApiResponse<Boolean>>

    @Streaming
    @GET
    suspend fun downloadFileDirectly(
        @Url fileUrl: String
    ): Response<ResponseBody>
}
