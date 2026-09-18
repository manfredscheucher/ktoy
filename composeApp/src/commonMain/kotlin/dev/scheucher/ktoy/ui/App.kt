package dev.scheucher.ktoy.ui

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
        Surface(Modifier.fillMaxSize()) {
            val vm: SimViewModel = viewModel { SimViewModel() }
            var onSimulator by remember { mutableStateOf(false) }

            // Android system-back on the simulator returns to the example list
            // instead of exiting the app.
            BackHandler(enabled = onSimulator) { onSimulator = false }

            if (onSimulator) {
                SimulatorScreen(vm = vm, onBack = { onSimulator = false })
            } else {
                ExampleListScreen(onPick = { example ->
                    vm.load(example)
                    onSimulator = true
                })
            }
        }
    }
}

@Composable
private fun ExampleListScreen(onPick: (Example) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ktoy — pick a program", style = MaterialTheme.typography.titleLarge)
        Text(
            "A Toy CPU simulator. Tap an example to step through it.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
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
