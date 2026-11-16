package app.voicetranslator.data.model

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ModelCatalog(
    val version: Int = 1,
    val baseUrl: String = "",
    val packages: List<ModelPackage> = emptyList(),
)

@Serializable
data class ModelPackage(
    val id: String,
    val kind: String,
    val name: String,
    val description: String = "",
    val archive: String,
    val sizeBytes: Long,
    val sha256: String,
    val installPath: String,
    val required: Boolean = true,
)

class ModelCatalogRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val catalog: ModelCatalog by lazy {
        context.assets.open("model_catalog.json").use { stream ->
            json.decodeFromString<ModelCatalog>(stream.readBytes().decodeToString())
        }
    }

    fun byId(id: String): ModelPackage? = catalog.packages.firstOrNull { it.id == id }

    fun requiredPackages(): List<ModelPackage> = catalog.packages.filter { it.required }

    fun byKind(kind: String): List<ModelPackage> = catalog.packages.filter { it.kind == kind }
}
