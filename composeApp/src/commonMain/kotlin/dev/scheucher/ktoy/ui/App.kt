package dev.scheucher.ktoy.ui

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.scheucher.ktoy.core.EXAMPLES
import dev.scheucher.ktoy.core.Example

/**
 * The app root: two screens. The example list, and — after picking one — the
 * simulator. A tiny nav flag keeps them separate; the simulator's state lives in
 * a ViewModel that survives rotation.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    MaterialTheme {
        // Keep content out of the status bar (top) and the navigation bar /
        // camera cutout (bottom); the activity draws edge-to-edge.
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val vm: SimViewModel = viewModel { SimViewModel() }
            // Start on the simulator with an empty machine (like pytoy's
            // empty_gui); the Load button opens the example list.
            if (!vm.hasProgram) vm.loadEmpty()
            var showList by remember { mutableStateOf(false) }

            // System-back closes the example list if it's open.
            BackHandler(enabled = showList) { showList = false }

            SimulatorScreen(vm = vm, onLoad = { showList = true })

            if (showList) {
                ExampleListScreen(
                    onPick = { example ->
                        vm.load(example)
                        showList = false
                    },
                    onDismiss = { showList = false },
                )
            }
        }
    }
}

@Composable
private fun ExampleListScreen(onPick: (Example) -> Unit, onDismiss: () -> Unit) {
    // opaque overlay over the simulator so the list fully replaces it while open
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pick an example", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(EXAMPLES) { example ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { onPick(example) }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(example.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
