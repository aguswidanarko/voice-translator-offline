package app.voicetranslator.data.model

import android.content.Context
import java.io.File

class ModelStore(private val context: Context) {

    val root: File get() = File(context.filesDir, "models")

    fun dir(pkg: ModelPackage): File = File(root, pkg.installPath)

    fun isInstalled(pkg: ModelPackage): Boolean = File(dir(pkg), MARKER).exists()

    fun markInstalled(pkg: ModelPackage) {
        val dir = dir(pkg)
        dir.mkdirs()
        File(dir, MARKER).writeText(pkg.sha256)
    }

    fun delete(pkg: ModelPackage) {
        dir(pkg).deleteRecursively()
    }

    fun installedBytes(packages: List<ModelPackage>): Long =
        packages.filter(::isInstalled).sumOf { sizeOf(dir(it)) }

    fun totalBytes(packages: List<ModelPackage>): Long = packages.sumOf { it.sizeBytes }

    fun freeBytes(): Long = context.filesDir.usableSpace

    fun sizeOf(file: File): Long =
        if (!file.exists()) 0L
        else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun allDirectories(): List<File> = root.listFiles()?.toList().orEmpty()

    companion object {
        const val MARKER = ".installed"
    }
}
