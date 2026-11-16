package app.voicetranslator.ui

import java.util.Locale

fun Long.asMegabytes(): String = String.format(Locale.US, "%.0f MB", this / 1_000_000.0)

fun Long.asGigabytes(): String = String.format(Locale.US, "%.1f GB", this / 1_000_000_000.0)

fun sizeLabel(bytes: Long): String = if (bytes >= 1_000_000_000) bytes.asGigabytes() else bytes.asMegabytes()
