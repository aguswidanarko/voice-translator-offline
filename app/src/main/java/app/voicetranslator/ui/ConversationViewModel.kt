package app.voicetranslator.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.voicetranslator.AppContainer
import app.voicetranslator.audio.AudioPlayer
import app.voicetranslator.audio.AudioRecorder
import app.voicetranslator.data.db.HistoryItem
import app.voicetranslator.engine.Language
import app.voicetranslator.engine.TtsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationViewModel(private val container: AppContainer) : ViewModel() {

    private val engines = container.engines
    private val history = container.history
    private val settings = container.settings
    private val recorder = AudioRecorder()
    private val player = AudioPlayer()

    data class Entry(
        val id: Long,
        val sourceText: String,
        val translatedText: String,
        val source: Language,
        val target: Language,
        val createdAt: Long,
    )

    data class UiState(
        val source: Language = Language.INDONESIAN,
        val target: Language = Language.CHINESE,
        val entries: List<Entry> = emptyList(),
        val recording: Boolean = false,
        val busy: String? = null,
        val error: String? = null,
        val draft: String = "",
        val autoPlay: Boolean = true,
        val speed: Float = 1f,
        val modelsReady: Boolean = false,
        val missing: List<String> = emptyList(),
        val lastClip: TtsEngine.Clip? = null,
        val lastClipLanguage: Language? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            history.observeAll().collect { items ->
                _state.update { it.copy(entries = items.map(::toEntry)) }
            }
        }
        viewModelScope.launch {
            settings.settings.collect { value ->
                _state.update { it.copy(autoPlay = value.autoPlay, speed = value.ttsSpeed) }
            }
        }
        refreshModels()
    }

    private fun toEntry(item: HistoryItem) = Entry(
        id = item.id,
        sourceText = item.sourceText,
        translatedText = item.translatedText,
        source = Language.byCode(item.sourceLang),
        target = Language.byCode(item.targetLang),
        createdAt = item.createdAt,
    )

    fun refreshModels() {
        val missing = engines.missingPackages()
        _state.update { it.copy(modelsReady = missing.isEmpty(), missing = missing.map { p -> p.name }) }
    }

    fun onMicPressed() {
        val state = _state.value
        if (state.recording) {
            stopRecording()
            return
        }
        if (!state.modelsReady) {
            _state.update { it.copy(error = "Model offline belum lengkap. Unduh model terlebih dahulu.") }
            return
        }
        if (state.busy != null) return
        if (recorder.start()) {
            _state.update { it.copy(recording = true, error = null) }
        } else {
            _state.update { it.copy(error = "Mikrofon tidak dapat dibuka.") }
        }
    }

    private fun stopRecording() {
        val pcm = recorder.stop()
        _state.update { it.copy(recording = false) }
        val minSamples = (AudioRecorder.SAMPLE_RATE * 0.3f).toInt()
        if (pcm.size < minSamples) {
            _state.update { it.copy(error = "Rekaman terlalu pendek.") }
            return
        }
        val from = _state.value.source
        val to = _state.value.target
        viewModelScope.launch {
            val text = try {
                _state.update { it.copy(busy = "Mengenali ucapan…", error = null) }
                engines.transcribe(pcm, from)
            } catch (e: Throwable) {
                fail(e)
                return@launch
            }
            if (text.isBlank()) {
                _state.update { it.copy(busy = null, error = "Tidak ada ucapan yang terdeteksi.") }
                return@launch
            }
            runPipeline(text, from, to)
        }
    }

    fun translateDraft() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return
        if (!_state.value.modelsReady) {
            _state.update { it.copy(error = "Model offline belum lengkap. Unduh model terlebih dahulu.") }
            return
        }
        viewModelScope.launch { runPipeline(text, _state.value.source, _state.value.target) }
    }

    private suspend fun runPipeline(text: String, from: Language, to: Language) {
        try {
            _state.update { it.copy(busy = "Menerjemahkan…", error = null) }
            val result = engines.translate(text, from, to)
            val translated = result.text.trim()
            if (translated.isEmpty()) {
                _state.update { it.copy(busy = null, error = "Terjemahan kosong, coba kalimat lain.") }
                return
            }
            history.insert(
                HistoryItem(
                    sourceText = text,
                    translatedText = translated,
                    sourceLang = from.code,
                    targetLang = to.code,
                    createdAt = System.currentTimeMillis(),
                )
            )
            _state.update { it.copy(busy = null, draft = "") }
            if (_state.value.autoPlay) speak(translated, to)
        } catch (e: Throwable) {
            fail(e)
        }
    }

    private suspend fun speak(text: String, language: Language) {
        try {
            _state.update { it.copy(busy = "Menyiapkan suara…", error = null) }
            val clip = engines.synthesize(text, language, _state.value.speed)
            _state.update { it.copy(busy = null, lastClip = clip, lastClipLanguage = language) }
            player.play(clip.samples, clip.sampleRate)
        } catch (e: Throwable) {
            fail(e)
        }
    }

    fun replay(entry: Entry) {
        viewModelScope.launch { speak(entry.translatedText, entry.target) }
    }

    fun replayLast() {
        val clip = _state.value.lastClip ?: return
        player.play(clip.samples, clip.sampleRate)
    }

    fun stopPlayback() = player.stop()

    fun swapLanguages() = _state.update { it.copy(source = it.target, target = it.source) }

    fun setDraft(value: String) = _state.update { it.copy(draft = value) }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun fail(e: Throwable) {
        Log.e(TAG, "pipeline gagal", e)
        val message = when (e) {
            is OutOfMemoryError -> "Memori perangkat tidak cukup. Tutup aplikasi lain lalu coba lagi."
            else -> e.message ?: "Terjadi kesalahan."
        }
        _state.update { it.copy(busy = null, recording = false, error = message) }
    }

    override fun onCleared() {
        recorder.cancel()
        player.stop()
    }

    companion object {
        private const val TAG = "ConversationViewModel"
        val factory = containerFactory { ConversationViewModel(it) }
    }
}
