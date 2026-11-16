package app.voicetranslator.engine

enum class Language(val code: String, val asrCode: String, val label: String, val ttsPackage: String) {
    INDONESIAN("id", "id", "Indonesia", ModelIds.TTS_ID),
    CHINESE("zh", "zh", "Mandarin", ModelIds.TTS_ZH),
    ;

    fun other(): Language = if (this == INDONESIAN) CHINESE else INDONESIAN

    companion object {
        fun byCode(code: String): Language = entries.first { it.code == code }
    }
}

object ModelIds {
    const val ASR = "asr-multilingual"
    const val TTS_ID = "tts-id"
    const val TTS_ZH = "tts-zh"

    const val MT_ID_EN = "mt-id-en"
    const val MT_EN_ZH = "mt-en-zh"
    const val MT_ZH_EN = "mt-zh-en"
    const val MT_EN_ID = "mt-en-id"

    val all = listOf(ASR, TTS_ID, TTS_ZH, MT_ID_EN, MT_EN_ZH, MT_ZH_EN, MT_EN_ID)

    /** OPUS-MT has no direct Indonesian-Chinese pair, so both directions pivot through English. */
    fun mtPath(from: Language, to: Language): List<String> = when {
        from == Language.INDONESIAN && to == Language.CHINESE -> listOf(MT_ID_EN, MT_EN_ZH)
        from == Language.CHINESE && to == Language.INDONESIAN -> listOf(MT_ZH_EN, MT_EN_ID)
        else -> error("pasangan bahasa tidak didukung: ${from.code}->${to.code}")
    }

    fun ttsFor(language: Language): String = language.ttsPackage
}
