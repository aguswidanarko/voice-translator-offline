package app.voicetranslator.engine

import app.voicetranslator.data.model.ModelCatalogRepository
import app.voicetranslator.data.model.ModelPackage
import app.voicetranslator.data.model.ModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the native engines and guarantees the BRD memory rule: ASR, translation and TTS
 * are never resident at the same time. Every stage releases the previous stage first.
 */
class EngineHost(
    private val catalog: ModelCatalogRepository,
    private val store: ModelStore,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    private var asr: AsrEngine? = null
    private var mt: MtEngine? = null
    private var mtPair: Pair<Language, Language>? = null
    private var tts: TtsEngine? = null
    private var ttsLanguage: Language? = null

    val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 2)

    fun missingPackages(): List<ModelPackage> =
        catalog.catalog.packages.filter { it.required && !store.isInstalled(it) }

    fun isReady(): Boolean = missingPackages().isEmpty()

    fun releaseAllAsync() {
        scope.launch(Dispatchers.Default) {
            mutex.withLock { releaseAll() }
        }
    }

    suspend fun transcribe(pcm: FloatArray, language: Language): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            releaseMt()
            releaseTts()
            val engine = asr ?: AsrEngine.open(dir(ModelIds.ASR), threads).also { asr = it }
            engine.transcribe(pcm, language.asrCode)
        }
    }

    suspend fun translate(text: String, from: Language, to: Language): MtEngine.Result =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                releaseAsr()
                releaseTts()
                val engine = if (mt != null && mtPair == (from to to)) {
                    mt!!
                } else {
                    releaseMt()
                    val path = ModelIds.mtPath(from, to)
                    MtEngine.open(dir(path[0]), dir(path[1]), threads).also {
                        mt = it
                        mtPair = from to to
                    }
                }
                engine.translate(text)
            }
        }

    suspend fun synthesize(text: String, language: Language, speed: Float): TtsEngine.Clip =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                releaseAsr()
                releaseMt()
                val engine = if (tts != null && ttsLanguage == language) {
                    tts!!
                } else {
                    releaseTts()
                    TtsEngine.open(dir(ModelIds.ttsFor(language)), threads).also {
                        tts = it
                        ttsLanguage = language
                    }
                }
                engine.synthesize(text, speed)
            }
        }

    private fun dir(packageId: String): File {
        val pkg = catalog.byId(packageId) ?: error("paket model $packageId tidak ada di katalog")
        val dir = store.dir(pkg)
        check(dir.isDirectory) { "paket ${pkg.name} belum terpasang" }
        return dir
    }

    private fun releaseAsr() {
        asr?.close()
        asr = null
    }

    private fun releaseMt() {
        mt?.close()
        mt = null
        mtPair = null
    }

    private fun releaseTts() {
        tts?.close()
        tts = null
        ttsLanguage = null
    }

    private fun releaseAll() {
        releaseAsr()
        releaseMt()
        releaseTts()
    }
}
