package app.voicetranslator.engine

import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.Closeable
import java.io.File
import java.util.Locale

class AsrEngine private constructor(private val recognizer: OnlineRecognizer) : Closeable {

    fun transcribe(pcm: FloatArray, languageCode: String): String {
        val stream = recognizer.createStream()
        return try {
            if (languageCode.isNotBlank()) {
                runCatching { stream.setOption("language", languageCode) }
            }
            stream.acceptWaveform(pcm, SAMPLE_RATE)
            // The streaming transducer drops the last word without trailing silence.
            stream.acceptWaveform(FloatArray(SAMPLE_RATE / 2), SAMPLE_RATE)
            stream.inputFinished()
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            normalize(recognizer.getResult(stream).text, languageCode)
        } finally {
            stream.release()
        }
    }

    /** This multilingual model emits uppercase Latin script; MT is case sensitive. */
    private fun normalize(text: String, languageCode: String): String {
        val trimmed = text.trim()
        return if (languageCode == "id") trimmed.lowercase(Locale.ROOT) else trimmed
    }

    override fun close() = recognizer.release()

    companion object {
        const val SAMPLE_RATE = 16000

        fun open(dir: File, threads: Int = 2): AsrEngine {
            val encoder = pick(dir, "encoder")
            val decoder = pick(dir) { it.contains("decoder") && !it.contains("with_past") }
            val joiner = pick(dir, "joiner")
            val tokens = File(dir, "tokens.txt")
            require(tokens.isFile) { "tokens.txt tidak ditemukan di ${dir.path}" }

            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0f),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        joiner = joiner.absolutePath,
                    ),
                    tokens = tokens.absolutePath,
                    numThreads = threads,
                    debug = false,
                    provider = "cpu",
                    modelType = "zipformer",
                ),
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 2.4f, 0f),
                    rule2 = EndpointRule(true, 1.2f, 0f),
                    rule3 = EndpointRule(false, 20f, 0f),
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search",
            )
            return AsrEngine(OnlineRecognizer(null, config))
        }

        private fun pick(dir: File, keyword: String): File = pick(dir) { it.contains(keyword) }

        private fun pick(dir: File, predicate: (String) -> Boolean): File {
            val matches = dir.listFiles()
                ?.filter { it.isFile && predicate(it.name.lowercase()) }
                .orEmpty()
            return matches.firstOrNull { "int8" in it.name.lowercase() }
                ?: matches.minByOrNull { it.name.length }
                ?: error("berkas model tidak ditemukan di ${dir.path}")
        }
    }
}
