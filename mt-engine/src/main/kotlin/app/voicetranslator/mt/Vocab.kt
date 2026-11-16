package app.voicetranslator.mt

import java.io.File

/**
 * Source side vocabulary extracted from SentencePiece.
 * Ids come from the model's vocab.json, which does NOT match the SPM internal ids.
 */
class SourceVocab private constructor(
    val byPiece: HashMap<String, Entry>,
    val unkId: Long,
    val minScore: Double,
    val maxPieceChars: Int,
) {
    data class Entry(val piece: String, val id: Long, val score: Double)

    companion object {
        fun load(file: File, unkId: Long = 0L): SourceVocab {
            val byPiece = HashMap<String, Entry>(40_000)
            var minScore = Double.MAX_VALUE
            var maxLen = 0
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty()) continue
                    val tab1 = line.indexOf('\t')
                    if (tab1 <= 0) continue
                    val tab2 = line.indexOf('\t', tab1 + 1)
                    if (tab2 < 0) continue
                    val piece = line.substring(0, tab1)
                    val id = line.substring(tab1 + 1, tab2).toLongOrNull() ?: continue
                    val score = line.substring(tab2 + 1).toDoubleOrNull() ?: continue
                    byPiece[piece] = Entry(piece, id, score)
                    if (score < minScore) minScore = score
                    if (piece.length > maxLen) maxLen = piece.length
                }
            }
            if (byPiece.isEmpty()) error("empty source vocab: ${file.absolutePath}")
            return SourceVocab(byPiece, unkId, minScore, minOf(maxLen, 32))
        }
    }
}

/** Target side vocabulary: id -> piece, used only for detokenization. */
class TargetVocab private constructor(private val pieces: Array<String>) {
    val size: Int get() = pieces.size

    fun piece(id: Int): String? = pieces.getOrNull(id)

    companion object {
        fun load(file: File): TargetVocab {
            var max = 0
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val id = line.substring(0, tab).toIntOrNull() ?: continue
                    if (id > max) max = id
                }
            }
            val arr = arrayOfNulls<String>(max + 1)
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val id = line.substring(0, tab).toIntOrNull() ?: continue
                    arr[id] = line.substring(tab + 1)
                }
            }
            @Suppress("UNCHECKED_CAST")
            val pieces = (arr as Array<String?>).map { it ?: "" }.toTypedArray()
            return TargetVocab(pieces)
        }
    }
}
