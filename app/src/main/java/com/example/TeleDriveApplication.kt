package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.repository.TeleDriveRepository
import com.example.network.TelegramClientProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TeleDriveApplication : Application() {

    lateinit var repository: TeleDriveRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        repository = TeleDriveRepository(this, database)

        // Restore active proxy if any
        CoroutineScope(Dispatchers.IO).launch {
            val activeProxy = database.proxyDao().getActiveProxyDirect()
            if (activeProxy != null) {
                TelegramClientProvider.updateProxy(
                    TelegramClientProvider.ProxySetting(
                        type = activeProxy.type,
                        host = activeProxy.host,
                        port = activeProxy.port,
                        username = activeProxy.username,
                        password = activeProxy.password
                    )
                )
            }
        }
    }
}
