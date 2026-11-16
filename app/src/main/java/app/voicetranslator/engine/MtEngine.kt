package app.voicetranslator.engine

import app.voicetranslator.mt.MarianTranslator
import app.voicetranslator.mt.PivotTranslator
import java.io.Closeable
import java.io.File

/** Two Marian models chained through English (the pivot language). */
class MtEngine private constructor(private val pivot: PivotTranslator) : Closeable {

    data class Result(val text: String, val pivotText: String)

    fun translate(text: String): Result {
        val (english, target) = pivot.lastPivotText(text)
        return Result(target, english)
    }

    override fun close() = pivot.close()

    companion object {
        fun open(firstStage: File, secondStage: File, threads: Int = 2): MtEngine =
            MtEngine(
                PivotTranslator(
                    MarianTranslator.open(firstStage, threads),
                    MarianTranslator.open(secondStage, threads),
                )
            )
    }
}
