package app.voicetranslator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.voicetranslator.BuildConfig
import app.voicetranslator.ui.SettingsViewModel
import app.voicetranslator.ui.sizeLabel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()

    var speed by remember { mutableFloatStateOf(settings.ttsSpeed) }
    var baseUrl by remember { mutableStateOf(settings.baseUrlOverride) }

    LaunchedEffect(settings.ttsSpeed) { speed = settings.ttsSpeed }
    LaunchedEffect(settings.baseUrlOverride) { baseUrl = settings.baseUrlOverride }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Suara terjemahan", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Kecepatan ${String.format(Locale.US, "%.1f", speed)}×",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    onValueChangeFinished = { viewModel.setTtsSpeed(speed) },
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Putar otomatis", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Langsung memutar suara setelah terjemahan selesai.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.autoPlay, onCheckedChange = viewModel::setAutoPlay)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sumber unduhan model", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Kosongkan untuk memakai URL bawaan aplikasi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                )
                Button(onClick = { viewModel.saveBaseUrl(baseUrl) }) {
                    Text("Simpan URL")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Memori", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Model terpakai: ${sizeLabel(storage.usedBytes)} · ruang kosong: ${sizeLabel(storage.freeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Model dilepas otomatis saat aplikasi tertekan memori (RAM 4 GB).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = viewModel::releaseEngines) {
                    Text("Lepas model dari memori sekarang")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tentang aplikasi", style = MaterialTheme.typography.titleSmall)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Voice Translator ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Penerjemah suara offline Indonesia ↔ Mandarin. " +
                        "ASR, penerjemahan, dan TTS berjalan sepenuhnya di perangkat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Mesin: sherpa-onnx (ASR/TTS), OPUS-MT Marian int8 (terjemahan), ONNX Runtime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
