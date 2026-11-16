package app.voicetranslator.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.voicetranslator.engine.Language
import app.voicetranslator.ui.ConversationViewModel

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.onMicPressed()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshModels()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!state.modelsReady) {
            MissingModelsBanner(
                missing = state.missing,
                onOpenModels = onOpenModels,
            )
        }

        LanguageBar(
            source = state.source,
            target = state.target,
            onSwap = viewModel::swapLanguages,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.entries.isEmpty()) {
                item {
                    EmptyConversation()
                }
            }
            items(state.entries, key = { it.id }) { entry ->
                EntryCard(
                    entry = entry,
                    onReplay = { viewModel.replay(entry) },
                )
            }
        }

        state.error?.let { message ->
            ErrorBanner(message = message, onDismiss = viewModel::clearError)
        }

        InputRow(
            draft = state.draft,
            enabled = state.modelsReady && state.busy == null,
            onDraftChange = viewModel::setDraft,
            onSend = viewModel::translateDraft,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        MicArea(
            recording = state.recording,
            busy = state.busy,
            permissionDenied = permissionDenied,
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.onMicPressed()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )
    }
}

@Composable
private fun MissingModelsBanner(missing: List<String>, onOpenModels: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Model offline belum lengkap",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                if (missing.isEmpty()) "Buka pengelola model untuk mengunduh."
                else "Belum terpasang: ${missing.joinToString(", ")}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onOpenModels) {
                Text("Buka Pengelola Model")
            }
        }
    }
}

@Composable
private fun LanguageBar(
    source: Language,
    target: Language,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguagePill(language = source, caption = "Sumber", modifier = Modifier.weight(1f))
        IconButton(onClick = onSwap) {
            Icon(Icons.Default.SwapHoriz, contentDescription = "Tukar bahasa")
        }
        LanguagePill(language = target, caption = "Tujuan", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LanguagePill(language: Language, caption: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                language.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EmptyConversation() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Mulai percakapan", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tekan tombol mikrofon dan bicara, atau tulis teks di bawah. " +
                "Semua proses berjalan offline di perangkat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryCard(
    entry: ConversationViewModel.Entry,
    onReplay: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                entry.source.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(entry.sourceText, style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text(
                entry.target.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                entry.translatedText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onReplay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Putar ulang")
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(entry.translatedText)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin terjemahan")
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    }
}

@Composable
private fun InputRow(
    draft: String,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Tulis teks…") },
            maxLines = 3,
            enabled = enabled,
        )
        FilledIconButton(onClick = onSend, enabled = enabled && draft.isNotBlank()) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Terjemahkan")
        }
    }
}

@Composable
private fun MicArea(
    recording: Boolean,
    busy: String?,
    permissionDenied: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(80.dp),
            enabled = busy == null,
            shape = RoundedCornerShape(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (recording) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                if (recording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (recording) "Berhenti merekam" else "Mulai bicara",
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            busy != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(busy, style = MaterialTheme.typography.bodySmall)
            }
            recording -> Text("Merekam… tekan lagi untuk berhenti", style = MaterialTheme.typography.bodySmall)
            permissionDenied -> Text(
                "Izin mikrofon ditolak. Aktifkan di pengaturan aplikasi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            else -> Text("Tekan untuk bicara", style = MaterialTheme.typography.bodySmall)
        }
    }
}
