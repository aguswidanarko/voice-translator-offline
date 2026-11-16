package app.voicetranslator.engine

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.Closeable
import java.io.File

class TtsEngine private constructor(private val tts: OfflineTts) : Closeable {

    data class Clip(val samples: FloatArray, val sampleRate: Int)

    fun synthesize(text: String, speed: Float = 1f): Clip {
        val audio = tts.generate(text, 0, speed.coerceIn(0.5f, 2.0f))
        return Clip(audio.samples, audio.sampleRate)
    }

    override fun close() = tts.release()

    companion object {
        /**
         * Piper models either ship a lexicon (Mandarin) or espeak-ng data (Indonesian).
         * sherpa-onnx >= 2.0 no longer builds an espeak frontend for Piper, so the app
         * pins 1.13.8 where `dataDir` still drives the espeak frontend.
         */
        fun open(modelDir: File, threads: Int = 2): TtsEngine {
            val model = findModel(modelDir)
            val tokens = File(modelDir, "tokens.txt")
            require(tokens.isFile) { "tokens.txt tidak ditemukan di ${modelDir.path}" }
            val lexicon = File(modelDir, "lexicon.txt").takeIf { it.isFile }
            val dataDir = File(modelDir, "espeak-ng-data").takeIf { it.isDirectory }
            require(lexicon != null || dataDir != null) {
                "model TTS di ${modelDir.path} tidak punya lexicon.txt maupun espeak-ng-data"
            }

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = model.absolutePath,
                        tokens = tokens.absolutePath,
                        dataDir = dataDir?.absolutePath ?: "",
                        lexicon = lexicon?.absolutePath ?: "",
                    ),
                    numThreads = threads,
                    debug = false,
                    provider = "cpu",
                ),
                ruleFsts = "",
                ruleFars = "",
                maxNumSentences = 1,
            )
            return TtsEngine(OfflineTts(null, config))
        }

        private fun findModel(dir: File): File {
            val matches = dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".onnx") }
                .orEmpty()
            return matches.firstOrNull { it.name.contains("int8") }
                ?: matches.firstOrNull()
                ?: error("model TTS tidak ditemukan di ${dir.path}")
        }
    }
}
