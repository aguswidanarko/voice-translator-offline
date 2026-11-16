package app.voicetranslator.mt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

data class MarianConfig(
    val layers: Int,
    val heads: Int,
    val headDim: Int,
    val bosId: Long,
    val eosId: Long,
    val padId: Long,
    val maxTargetLength: Int = 96,
    val threads: Int = 4,
) {
    val dModel: Int get() = heads * headDim
}

/**
 * Marian (OPUS-MT) translator running on ONNX Runtime.
 * Uses encoder + decoder prefill + decoder-with-past, greedy search, int8 weights.
 */
class MarianTranslator(
    modelDir: File,
    private val config: MarianConfig,
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment(),
) : Closeable {

    private val sourceVocab = SourceVocab.load(File(modelDir, "source_vocab.tsv"))
    private val targetVocab = TargetVocab.load(File(modelDir, "target_vocab.tsv"))
    private val tokenizer = SentencePieceTokenizer(sourceVocab)

    private val sessionOptions = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(config.threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }

    private val encoder = env.createSession(File(modelDir, "encoder.int8.onnx").absolutePath, sessionOptions)
    private val decoder = env.createSession(File(modelDir, "decoder.int8.onnx").absolutePath, sessionOptions)
    private val decoderWithPast = env.createSession(File(modelDir, "decoder_with_past.int8.onnx").absolutePath, sessionOptions)

    @Volatile
    private var closed = false

    fun translate(text: String, maxLength: Int = config.maxTargetLength): String {
        check(!closed) { "translator closed" }
        val srcIds = tokenizer.encode(text)
        if (srcIds.isEmpty()) return ""

        val srcLen = srcIds.size + 1
        val srcLong = LongArray(srcLen)
        srcIds.copyInto(srcLong)
        srcLong[srcLen - 1] = config.eosId
        val srcMask = LongArray(srcLen) { 1L }

        val hidden: FloatArray
        val srcShape = longArrayOf(1, srcLen.toLong(), config.dModel.toLong())
        encoder.run(
            mapOf(
                "input_ids" to longTensor(longArrayOf(1, srcLen.toLong()), srcLong),
                "attention_mask" to longTensor(longArrayOf(1, srcLen.toLong()), srcMask),
            )
        ).use { result ->
            val tensor = result.get(0) as OnnxTensor
            hidden = FloatArray(srcLen * config.dModel)
            tensor.floatBuffer.get(hidden)
        }

        val encKv = Array(config.layers) { arrayOfNulls<FloatArray>(2) }
        val decKv = Array(config.layers) { arrayOfNulls<FloatArray>(2) }

        var logits: FloatArray
        var vocabSize: Int

        decoder.run(
            mapOf(
                "input_ids" to longTensor(longArrayOf(1, 1), longArrayOf(config.bosId)),
                "encoder_hidden_states" to floatTensor(srcShape, hidden),
                "encoder_attention_mask" to longTensor(longArrayOf(1, srcLen.toLong()), srcMask),
            )
        ).use { result ->
            val logitsTensor = output(result, "logits")
            vocabSize = logitsTensor.info.shape.last().toInt()
            logits = FloatArray(vocabSize)
            logitsTensor.floatBuffer.get(logits)
            for (layer in 0 until config.layers) {
                encKv[layer][0] = copyFloats(result, "present.$layer.encoder.key")
                encKv[layer][1] = copyFloats(result, "present.$layer.encoder.value")
                decKv[layer][0] = copyFloats(result, "present.$layer.decoder.key")
                decKv[layer][1] = copyFloats(result, "present.$layer.decoder.value")
            }
        }

        val outIds = ArrayList<Int>(32)
        var pastLen = 1
        var step = 0
        while (step < maxLength) {
            val next = argMax(logits, minOf(vocabSize, targetVocab.size))
            if (next == config.eosId.toInt() || next < 0) break
            outIds.add(next)

            val inputs = HashMap<String, OnnxTensor>(4 + config.layers * 4)
            inputs["input_ids"] = longTensor(longArrayOf(1, 1), longArrayOf(next.toLong()))
            inputs["encoder_attention_mask"] = longTensor(longArrayOf(1, srcLen.toLong()), srcMask)
            for (layer in 0 until config.layers) {
                inputs["past_key_values.$layer.decoder.key"] =
                    floatTensor(longArrayOf(1, config.heads.toLong(), pastLen.toLong(), config.headDim.toLong()), decKv[layer][0]!!)
                inputs["past_key_values.$layer.decoder.value"] =
                    floatTensor(longArrayOf(1, config.heads.toLong(), pastLen.toLong(), config.headDim.toLong()), decKv[layer][1]!!)
                inputs["past_key_values.$layer.encoder.key"] =
                    floatTensor(longArrayOf(1, config.heads.toLong(), srcLen.toLong(), config.headDim.toLong()), encKv[layer][0]!!)
                inputs["past_key_values.$layer.encoder.value"] =
                    floatTensor(longArrayOf(1, config.heads.toLong(), srcLen.toLong(), config.headDim.toLong()), encKv[layer][1]!!)
            }

            decoderWithPast.run(inputs).use { result ->
                val logitsTensor = output(result, "logits")
                logits = FloatArray(vocabSize)
                logitsTensor.floatBuffer.get(logits)
                for (layer in 0 until config.layers) {
                    decKv[layer][0] = copyFloats(result, "present.$layer.decoder.key")
                    decKv[layer][1] = copyFloats(result, "present.$layer.decoder.value")
                }
            }
            inputs.values.forEach { it.close() }
            pastLen += 1
            step++
        }

        return detokenize(outIds, targetVocab)
    }

    override fun close() {
        if (closed) return
        closed = true
        encoder.close()
        decoder.close()
        decoderWithPast.close()
        sessionOptions.close()
    }

    private fun copyFloats(result: OrtSession.Result, name: String): FloatArray {
        val tensor = output(result, name)
        val out = FloatArray(tensor.floatBuffer.remaining())
        tensor.floatBuffer.get(out)
        return out
    }

    private fun output(result: OrtSession.Result, name: String): OnnxTensor =
        (result.get(name).orElse(null) as? OnnxTensor)
            ?: error("missing ONNX output '$name'")

    private fun argMax(values: FloatArray, size: Int): Int {
        var best = -1
        var bestValue = Float.NEGATIVE_INFINITY
        for (i in 0 until size) {
            val v = values[i]
            if (v > bestValue) {
                bestValue = v
                best = i
            }
        }
        return best
    }

    private fun longTensor(shape: LongArray, data: LongArray): OnnxTensor =
        OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape)

    private fun floatTensor(shape: LongArray, data: FloatArray): OnnxTensor =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)

    companion object {
        fun open(modelDir: File, threads: Int = 4): MarianTranslator {
            val meta = ModelMeta.load(modelDir)
            return MarianTranslator(modelDir, meta.toConfig(threads))
        }
    }
}
