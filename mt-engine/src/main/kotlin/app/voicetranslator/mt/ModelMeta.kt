package app.voicetranslator.mt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ModelMeta(
    val id: String,
    val source: String,
    val target: String,
    val bosTokenId: Long,
    val eosTokenId: Long,
    val padTokenId: Long,
    val decoderLayers: Int,
    val attentionHeads: Int,
    val dModel: Int,
    val headDim: Int,
    val maxTargetLength: Int = 96,
) {
    fun toConfig(threads: Int) = MarianConfig(
        layers = decoderLayers,
        heads = attentionHeads,
        headDim = headDim,
        bosId = bosTokenId,
        eosId = eosTokenId,
        padId = padTokenId,
        maxTargetLength = maxTargetLength,
        threads = threads,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(dir: File): ModelMeta =
            json.decodeFromString(serializer(), File(dir, "model.json").readText())
    }
}
