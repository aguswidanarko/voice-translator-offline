package app.voicetranslator.mt

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarianTranslationTest {

    private val root: File = run {
        val fromEnv = System.getenv("MT_MODELS_DIR")
        if (fromEnv != null) File(fromEnv) else File("../models-out/mt")
    }

    private fun hasModel(name: String) = File(root, name).isDirectory

    @Test
    fun idToEnglishMatchesReference() {
        if (!hasModel("opus-mt-id-en")) return
        MarianTranslator.open(File(root, "opus-mt-id-en")).use { t ->
            assertEquals("Good morning, how are you?", t.translate("Selamat pagi, apa kabar?"))
            assertEquals("Where's the nearest train station?", t.translate("Di mana stasiun kereta terdekat?"))
            assertEquals("How much for a room per night?", t.translate("Berapa harga kamar per malam?"))
            assertEquals("Please speak slower.", t.translate("Tolong bicara lebih lambat."))
        }
    }

    @Test
    fun englishToChinese() {
        if (!hasModel("opus-mt-en-zh")) return
        MarianTranslator.open(File(root, "opus-mt-en-zh")).use { t ->
            val out = t.translate("Where is the nearest train station?")
            println("EN->ZH: $out")
            assertTrue(out.any { it.code in 0x4E00..0x9FFF }, "expected Chinese characters, got: $out")
        }
    }

    @Test
    fun chineseToIndonesian() {
        if (!hasModel("opus-mt-zh-en") || !hasModel("opus-mt-en-id")) return
        MarianTranslator.open(File(root, "opus-mt-zh-en")).use { zhEn ->
            MarianTranslator.open(File(root, "opus-mt-en-id")).use { enId ->
                val english = zhEn.translate("最近的火车站在哪里？")
                val indo = enId.translate(english)
                println("ZH->EN: $english")
                println("EN->ID: $indo")
                assertTrue(indo.isNotBlank())
            }
        }
    }

    @Test
    fun indonesianToChinesePivot() {
        if (!hasModel("opus-mt-id-en") || !hasModel("opus-mt-en-zh")) return
        MarianTranslator.open(File(root, "opus-mt-id-en")).use { idEn ->
            MarianTranslator.open(File(root, "opus-mt-en-zh")).use { enZh ->
                val pivot = PivotTranslator(idEn, enZh)
                val samples = listOf(
                    "Selamat pagi, apa kabar?",
                    "Di mana stasiun kereta terdekat?",
                    "Saya ingin memesan dua tiket ke Beijing.",
                    "Tolong bicara lebih lambat.",
                )
                for (s in samples) {
                    val (english, chinese) = pivot.lastPivotText(s)
                    println("ID: $s")
                    println("  EN: $english")
                    println("  ZH: $chinese")
                    assertTrue(chinese.any { it.code in 0x4E00..0x9FFF }, "expected Chinese, got: $chinese")
                }
            }
        }
    }

    @Test
    fun benchmarkShortSentences() {
        if (!hasModel("opus-mt-id-en")) return
        MarianTranslator.open(File(root, "opus-mt-id-en"), threads = 4).use { t ->
            val text = "Saya ingin memesan dua tiket ke Beijing besok pagi."
            t.translate(text) // warm-up
            val runs = 5
            val start = System.nanoTime()
            repeat(runs) { t.translate(text) }
            val ms = (System.nanoTime() - start) / 1_000_000.0 / runs
            println("id->en avg %.1f ms/sentence (desktop CPU)".format(ms))
        }
    }
}
