package snastro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Wave-0 scaffold placeholder: proves the Compose Desktop render path end to end (headless
 * `:ui:renderCheck`, `:avvio` `--smoke`) before any real screen (S1-S4) exists.
 */
@Composable
fun SchermataPlaceholder() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("snastro")
        }
    }
}
