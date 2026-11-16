package app.voicetranslator.mt

import java.text.Normalizer

/**
 * SentencePiece unigram tokenizer (model_type = UNIGRAM, nmt_nfkc, add_dummy_prefix=true),
 * re-implemented so no native sentencepiece library is needed on Android.
 */
class SentencePieceTokenizer(
    private val vocab: SourceVocab,
    private val unkPenalty: Double = 10.0,
) {
    private val unkScore = vocab.minScore - unkPenalty

    fun encode(text: String): LongArray {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return LongArray(0)

        val sb = StringBuilder(normalized.length + 2)
        sb.append('\u2581')
        for (ch in normalized) sb.append(if (ch == ' ') '\u2581' else ch)
        val chars: CharArray = sb.toString().toCharArray()
        val n = chars.size
        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val prev = IntArray(n + 1) { -1 }
        val ids = LongArray(n + 1) { vocab.unkId }
        best[0] = 0.0

        val maxLen = vocab.maxPieceChars
        for (i in 0 until n) {
            if (best[i] == Double.NEGATIVE_INFINITY) continue

            // unknown single char fallback
            val unk = best[i] + unkScore
            if (unk > best[i + 1]) {
                best[i + 1] = unk
                prev[i + 1] = i
                ids[i + 1] = vocab.unkId
            }

            val limit = minOf(maxLen, n - i)
            var len = 1
            while (len <= limit) {
                val entry = vocab.byPiece[String(chars, i, len)]
                if (entry != null) {
                    val score = best[i] + entry.score
                    if (score > best[i + len]) {
                        best[i + len] = score
                        prev[i + len] = i
                        ids[i + len] = entry.id
                    }
                }
                len++
            }
        }

        if (prev[n] == -1) return LongArray(0)
        var count = 0
        var p = n
        while (p > 0) { count++; p = prev[p] }
        val out = LongArray(count)
        p = n
        var idx = count - 1
        while (p > 0) {
            out[idx--] = ids[p]
            p = prev[p]
        }
        return out
    }

    private fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val sb = StringBuilder(nfkc.length + 1)
        var pendingSpace = false
        for (ch in nfkc) {
            when {
                isSpace(ch) -> if (sb.isNotEmpty()) pendingSpace = true
                ch.code < 0x20 || ch.code == 0x7F -> Unit
                else -> {
                    if (pendingSpace) {
                        sb.append(' ')
                        pendingSpace = false
                    }
                    sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    private fun isSpace(ch: Char): Boolean =
        ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '\u00A0' ||
            ch == '\u2028' || ch == '\u2029' || ch == '\u3000' ||
            (ch.code in 0x2000..0x200A) || (ch.code in 0x1680..0x1680)
}

/** Turns target ids back into text the same way MarianTokenizer.decode does. */
fun detokenize(ids: List<Int>, vocab: TargetVocab): String {
    val sb = StringBuilder()
    for (id in ids) {
        val piece = vocab.piece(id) ?: continue
        if (piece == "</s>" || piece == "<pad>") continue
        sb.append(piece)
    }
    return sb.toString().replace('\u2581', ' ').trim()
}
