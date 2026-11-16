package app.voicetranslator.mt

import java.io.Closeable

/**
 * Two-hop translation. OPUS-MT has no direct Indonesian-Chinese model, so
 * ID -> ZH goes ID -> EN -> ZH and ZH -> ID goes ZH -> EN -> ID.
 */
class PivotTranslator(
    private val toEnglish: MarianTranslator,
    private val fromEnglish: MarianTranslator,
) : Closeable {

    fun translate(text: String): String {
        val english = toEnglish.translate(text)
        if (english.isBlank()) return ""
        return fromEnglish.translate(english)
    }

    fun lastPivotText(text: String): Pair<String, String> {
        val english = toEnglish.translate(text)
        if (english.isBlank()) return english to ""
        return english to fromEnglish.translate(english)
    }

    override fun close() {
        toEnglish.close()
        fromEnglish.close()
    }
}
