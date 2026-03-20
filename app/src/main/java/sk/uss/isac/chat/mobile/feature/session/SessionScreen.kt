package sk.uss.isac.chat.mobile.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SessionRoute(viewModel: SessionViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SessionScreen(
        uiState = uiState,
        onBaseUrlChanged = viewModel::onBaseUrlChanged,
        onWsUrlChanged = viewModel::onWsUrlChanged,
        onTokenChanged = viewModel::onTokenChanged,
        onApiTypeChanged = viewModel::onApiTypeChanged,
        onSave = viewModel::saveSession
    )
}

@Composable
fun SessionScreen(
    uiState: SessionUiState,
    onBaseUrlChanged: (String) -> Unit,
    onWsUrlChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onApiTypeChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "ISAC Chat Mobile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Zatial pouzivame prakticky bootstrap pre mobil: API base URL, WebSocket URL a bearer token z existujuceho ISAC prostredia.",
            style = MaterialTheme.typography.bodyLarge
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.baseUrl,
                    onValueChange = onBaseUrlChanged,
                    label = { Text("Chat API base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.wsUrl,
                    onValueChange = onWsUrlChanged,
                    label = { Text("WebSocket URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.xApiType,
                    onValueChange = onApiTypeChanged,
                    label = { Text("X-Api-Type") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uiState.accessToken,
                    onValueChange = onTokenChanged,
                    label = { Text("Bearer token") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (!uiState.error.isNullOrBlank()) {
                    Text(
                        text = uiState.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Ulozit session a otvorit chat")
                    }
                }
            }
        }
    }
}
