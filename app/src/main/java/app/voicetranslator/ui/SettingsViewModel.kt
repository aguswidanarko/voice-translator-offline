package app.voicetranslator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.voicetranslator.AppContainer
import app.voicetranslator.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class StorageInfo(val usedBytes: Long = 0, val freeBytes: Long = 0)

    val settings = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _storage = MutableStateFlow(StorageInfo())
    val storage = _storage.asStateFlow()

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val used = withContext(Dispatchers.IO) {
                container.store.installedBytes(container.catalog.catalog.packages)
            }
            _storage.value = StorageInfo(used, container.store.freeBytes())
        }
    }

    fun setTtsSpeed(value: Float) = viewModelScope.launch {
        container.settings.setTtsSpeed(value)
    }

    fun setAutoPlay(value: Boolean) = viewModelScope.launch {
        container.settings.setAutoPlay(value)
    }

    fun saveBaseUrl(value: String) = viewModelScope.launch {
        container.settings.setBaseUrlOverride(value)
        refreshStorage()
    }

    fun releaseEngines() = container.engines.releaseAllAsync()

    companion object {
        val factory = containerFactory { SettingsViewModel(it) }
    }
}
