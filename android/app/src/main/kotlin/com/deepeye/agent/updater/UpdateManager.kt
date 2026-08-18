package com.deepeye.agent.updater

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpToDate(val version: String) : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

@Singleton
class UpdateManager @Inject constructor(
    private val checker: GitHubUpdateChecker,
    private val downloader: UpdateDownloader,
    private val installer: UpdateInstaller,
    private val prefs: UpdatePreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun checkForUpdates(force: Boolean = false) {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) {
            return
        }

        // Optional: debounce logic
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLastCheckTime()
        // If not forced and checked within last 12 hours, ignore.
        if (!force && now - lastCheck < 12 * 60 * 60 * 1000) {
            return
        }

        _updateState.value = UpdateState.Checking
        scope.launch {
            val result = checker.checkForUpdate()
            prefs.setLastCheckTime(System.currentTimeMillis())
            
            result.onSuccess { info ->
                if (info.isUpdateAvailable) {
                    if (force || !prefs.isVersionDismissed(info.latestVersion)) {
                        _updateState.value = UpdateState.Available(info)
                    } else {
                        _updateState.value = UpdateState.Idle
                    }
                } else {
                    if (force) {
                        _updateState.value = UpdateState.UpToDate(info.latestVersion)
                    } else {
                        _updateState.value = UpdateState.Idle
                    }
                }
            }.onFailure { e ->
                if (force) {
                    _updateState.value = UpdateState.Error(e.message ?: "Failed to check for updates")
                } else {
                    _updateState.value = UpdateState.Idle
                }
            }
        }
    }

    fun dismissUpdate(version: String) {
        prefs.setVersionDismissed(version, true)
        _updateState.value = UpdateState.Idle
    }

    fun startDownload(url: String) {
        scope.launch {
            downloader.downloadUpdate(url).collect { state ->
                when (state) {
                    is UpdateDownloadState.Progress -> {
                        _updateState.value = UpdateState.Downloading(state.percent)
                    }
                    is UpdateDownloadState.Success -> {
                        _updateState.value = UpdateState.ReadyToInstall(state.file)
                    }
                    is UpdateDownloadState.Error -> {
                        _updateState.value = UpdateState.Error(state.message)
                    }
                }
            }
        }
    }

    fun installUpdate(apkFile: File) {
        installer.installApk(apkFile)
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
