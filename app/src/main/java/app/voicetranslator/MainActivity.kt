package app.voicetranslator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.voicetranslator.ui.ConversationViewModel
import app.voicetranslator.ui.HistoryViewModel
import app.voicetranslator.ui.ModelsViewModel
import app.voicetranslator.ui.SettingsViewModel
import app.voicetranslator.ui.screens.ConversationScreen
import app.voicetranslator.ui.screens.HistoryScreen
import app.voicetranslator.ui.screens.ModelsScreen
import app.voicetranslator.ui.screens.SettingsScreen
import app.voicetranslator.ui.theme.VoiceTranslatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceTranslatorTheme {
                AppRoot()
            }
        }
    }
}

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    CONVERSE("converse", "Terjemah", Icons.Default.RecordVoiceOver),
    MODELS("models", "Model", Icons.Default.Storage),
    HISTORY("history", "Riwayat", Icons.Default.History),
    SETTINGS("settings", "Pengaturan", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val current = Destination.entries.firstOrNull { it.route == currentRoute } ?: Destination.CONVERSE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Voice Translator", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Offline · Indonesia ↔ Mandarin",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination,
                        onClick = {
                            if (current != destination) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.CONVERSE.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.CONVERSE.route) {
                val viewModel: ConversationViewModel = viewModel(factory = ConversationViewModel.factory)
                ConversationScreen(
                    viewModel = viewModel,
                    onOpenModels = { navController.navigate(Destination.MODELS.route) },
                )
            }
            composable(Destination.MODELS.route) {
                val viewModel: ModelsViewModel = viewModel(factory = ModelsViewModel.factory)
                ModelsScreen(viewModel = viewModel)
            }
            composable(Destination.HISTORY.route) {
                val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory)
                HistoryScreen(viewModel = viewModel)
            }
            composable(Destination.SETTINGS.route) {
                val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory)
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
