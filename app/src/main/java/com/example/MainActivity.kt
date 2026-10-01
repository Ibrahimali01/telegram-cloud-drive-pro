package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.TransferProgressDialog
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.ApiCredentialsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.ProxyScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UploadScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TeleDriveViewModel

enum class Screen {
    HOME,
    ACCOUNTS,
    API_CREDENTIALS,
    UPLOAD,
    PROXY,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: TeleDriveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
                val activeMedia by viewModel.activeMedia.collectAsStateWithLifecycle()
                val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

                var currentScreen by remember { mutableStateOf(Screen.HOME) }

                LaunchedEffect(statusMessage) {
                    statusMessage?.let { msg ->
                        snackbarHostState.showSnackbar(
                            message = msg,
                            duration = SnackbarDuration.Short
                        )
                        viewModel.clearStatusMessage()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentScreen) {
                            Screen.HOME -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateUpload = { currentScreen = Screen.UPLOAD },
                                    onNavigateAccounts = { currentScreen = Screen.ACCOUNTS },
                                    onNavigateProxy = { currentScreen = Screen.PROXY },
                                    onNavigateSettings = { currentScreen = Screen.SETTINGS }
                                )
                            }
                            Screen.ACCOUNTS -> {
                                BackHandler { currentScreen = Screen.HOME }
                                AccountsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = Screen.HOME },
                                    onNavigateApiCredentials = { currentScreen = Screen.API_CREDENTIALS },
                                    onNavigateUpload = { currentScreen = Screen.UPLOAD }
                                )
                            }
                            Screen.API_CREDENTIALS -> {
                                BackHandler { currentScreen = Screen.ACCOUNTS }
                                ApiCredentialsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = Screen.ACCOUNTS },
                                    onNavigateToAccounts = { currentScreen = Screen.ACCOUNTS }
                                )
                            }
                            Screen.UPLOAD -> {
                                BackHandler { currentScreen = Screen.HOME }
                                UploadScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = Screen.HOME }
                                )
                            }
                            Screen.PROXY -> {
                                BackHandler { currentScreen = Screen.HOME }
                                ProxyScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = Screen.HOME }
                                )
                            }
                            Screen.SETTINGS -> {
                                BackHandler { currentScreen = Screen.HOME }
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = Screen.HOME },
                                    onNavigateApiCredentials = { currentScreen = Screen.API_CREDENTIALS }
                                )
                            }
                        }

                        // Media player / viewer overlay
                        activeMedia?.let { (file, localFile) ->
                            BackHandler { viewModel.closeMediaViewer() }
                            MediaViewerScreen(
                                file = file,
                                localFile = localFile,
                                onClose = { viewModel.closeMediaViewer() }
                            )
                        }

                        // Ongoing download/decryption progress dialog
                        downloadProgress?.let { prog ->
                            TransferProgressDialog(progress = prog)
                        }
                    }
                }
            }
        }
    }
}
