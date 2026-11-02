package com.dopavin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dopavin.ui.MusicApp
import com.dopavin.ui.BackupRequest
import com.dopavin.ui.MusicViewModel
import com.dopavin.ui.theme.MusicTheme
import com.dopavin.data.SettingsStore
import com.dopavin.data.ThemeMode
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
    private val viewModel: MusicViewModel by viewModels()
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("audio/*")) { uri ->
            viewModel.onSaveTarget(uri)
        }
    private val exportBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            viewModel.onBackupExportTarget(uri)
        }
    private val importBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            viewModel.onBackupImportSource(uri)
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        if (CrashReporter.pending(this) != null) {
            startActivity(android.content.Intent(this, CrashActivity::class.java))
        }
        val startupSettings = SettingsStore(this).load()
        val startupTheme = startupSettings.theme
        val startupDark = when (startupTheme) {
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
            ThemeMode.System ->
                (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                when {
                    !startupDark -> 0xFFFAF7F5.toInt()
                    startupSettings.pureBlack -> 0xFF000000.toInt()
                    else -> 0xFF0B0B10.toInt()
                },
            ),
        )
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            viewModel.playback.first { it.isPlaying }
            askNotificationsOnce()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingSave.collect { song ->
                    if (song != null) {
                        val extension = song.uri.lastPathSegment?.substringAfterLast('.', "m4a") ?: "m4a"
                        val name = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
                        runCatching { createDocument.launch("$name.$extension") }
                            .onFailure { viewModel.clearPendingSave() }
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.backupRequest.collect { request ->
                    when (request) {
                        BackupRequest.Export -> runCatching { exportBackup.launch("dopavin-backup.json") }
                            .onFailure { viewModel.clearBackupRequest() }
                        BackupRequest.Import -> runCatching {
                            importBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                        }.onFailure { viewModel.clearBackupRequest() }
                        null -> Unit
                    }
                }
            }
        }
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (dark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                )
                onDispose { }
            }
            val playingSong by viewModel.currentSong.collectAsStateWithLifecycle()
            val artworkTint = com.dopavin.ui.components.rememberArtworkTint(
                if (settings.accentTint == com.dopavin.data.AccentTint.DYNAMIC) playingSong?.artworkUri else null,
            )
            MusicTheme(
                darkTheme = dark,
                pureBlack = settings.pureBlack,
                accentTint = settings.accentTint,
                artworkTint = artworkTint,
            ) {
                MusicApp(
                    viewModel = viewModel,
                )
            }
        }
    }
    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!viewModel.shouldAskNotifications()) return
        viewModel.markNotificationsAsked()
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestNotifications.launch(permission)
        }
    }
}
