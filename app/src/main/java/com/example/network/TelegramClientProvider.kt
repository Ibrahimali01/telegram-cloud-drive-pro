package com.example.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object TelegramClientProvider {

    private const val DEFAULT_BASE_URL = "https://api.telegram.org/"
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private var activeProxyConfig: ProxySetting? = null
    private var cachedService: TelegramApiService? = null
    private var cachedOkHttpClient: OkHttpClient? = null

    data class ProxySetting(
        val type: String, // "SOCKS5", "HTTP", "DIRECT"
        val host: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null
    )

    fun updateProxy(proxySetting: ProxySetting?) {
        activeProxyConfig = proxySetting
        cachedOkHttpClient = null
        cachedService = null
    }

    fun getOkHttpClient(): OkHttpClient {
        cachedOkHttpClient?.let { return it }

        val builder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        // Proxy handling
        activeProxyConfig?.let { cfg ->
            if (cfg.type != "DIRECT" && cfg.host.isNotBlank() && cfg.port > 0) {
                val proxyType = when (cfg.type.uppercase()) {
                    "SOCKS5", "SOCKS" -> Proxy.Type.SOCKS
                    "HTTP", "HTTPS" -> Proxy.Type.HTTP
                    else -> Proxy.Type.DIRECT
                }
                if (proxyType != Proxy.Type.DIRECT) {
                    val address = InetSocketAddress(cfg.host, cfg.port)
                    builder.proxy(Proxy(proxyType, address))

                    if (!cfg.username.isNullOrBlank() && !cfg.password.isNullOrBlank()) {
                        builder.proxyAuthenticator(object : Authenticator {
                            override fun authenticate(route: Route?, response: Response): Request? {
                                val credential = Credentials.basic(cfg.username, cfg.password)
                                return response.request.newBuilder()
                                    .header("Proxy-Authorization", credential)
                                    .build()
                            }
                        })
                    }
                }
            }
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        builder.addInterceptor(logging)

        val client = builder.build()
        cachedOkHttpClient = client
        return client
    }

    fun getApiService(baseUrl: String = DEFAULT_BASE_URL): TelegramApiService {
        cachedService?.let { return it }

        val client = getOkHttpClient()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val service = retrofit.create(TelegramApiService::class.java)
        cachedService = service
        return service
    }

    fun getDirectDownloadUrl(botToken: String, filePath: String): String {
        return "https://api.telegram.org/file/bot$botToken/$filePath"
    }
}
