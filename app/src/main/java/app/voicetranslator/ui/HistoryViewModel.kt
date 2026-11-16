package app.voicetranslator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.voicetranslator.AppContainer
import app.voicetranslator.data.db.HistoryItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    val items = container.history.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<HistoryItem>())

    fun delete(id: Long) = viewModelScope.launch { container.history.delete(id) }

    fun clear() = viewModelScope.launch { container.history.clear() }

    companion object {
        val factory = containerFactory { HistoryViewModel(it) }
    }
}
