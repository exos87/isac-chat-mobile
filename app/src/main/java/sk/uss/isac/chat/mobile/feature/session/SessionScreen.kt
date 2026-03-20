package sk.uss.isac.chat.mobile.feature.session

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.uss.isac.chat.mobile.BuildConfig

@Composable
fun SessionRoute(viewModel: SessionViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val importTokenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            viewModel.reportImportError("Vyber suboru s tokenom bol zruseny.")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
        }.onSuccess { token ->
            viewModel.importToken(token, "suboru")
        }.onFailure {
            viewModel.reportImportError("Token sa zo suboru nepodarilo nacitat.")
        }
    }
    SessionScreen(
        uiState = uiState,
        onBaseUrlChanged = viewModel::onBaseUrlChanged,
        onWsUrlChanged = viewModel::onWsUrlChanged,
        onTokenChanged = viewModel::onTokenChanged,
        onProfileApiUrlChanged = viewModel::onProfileApiUrlChanged,
        onApiTypeChanged = viewModel::onApiTypeChanged,
        onApplyLocalPreset = viewModel::applyLocalEmulatorPreset,
        onTestConnection = viewModel::testSession,
        onPasteTokenFromClipboard = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val token = clipboard
                ?.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                .orEmpty()
            viewModel.importToken(token, "clipboardu")
        },
        onImportTokenFromFile = {
            importTokenLauncher.launch(arrayOf("text/plain", "application/json", "*/*"))
        },
        onSave = viewModel::saveSession
    )
}

@Composable
fun SessionScreen(
    uiState: SessionUiState,
    onBaseUrlChanged: (String) -> Unit,
    onWsUrlChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onProfileApiUrlChanged: (String) -> Unit,
    onApiTypeChanged: (String) -> Unit,
    onApplyLocalPreset: () -> Unit,
    onTestConnection: () -> Unit,
    onPasteTokenFromClipboard: () -> Unit,
    onImportTokenFromFile: () -> Unit,
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
            text = "Zatial pouzivame prakticky bootstrap pre mobil: chat API URL, WebSocket URL, bearer token a volitelne profile API URL pre potvrdenie instalacie.",
            style = MaterialTheme.typography.bodyLarge
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Lokalny emulator preset pouziva 10.0.2.2:9880 pre REST a ws://10.0.2.2:9880/api/ws/chat pre realtime.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onApplyLocalPreset,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lokalny preset")
                    }
                    OutlinedButton(
                        onClick = onTestConnection,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isTesting && !uiState.isSaving
                    ) {
                        if (uiState.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Test spojenia")
                        }
                    }
                }
                if (BuildConfig.DEBUG) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPasteTokenFromClipboard,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Token z clipboardu")
                        }
                        OutlinedButton(
                            onClick = onImportTokenFromFile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Token zo suboru")
                        }
                    }
                }
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
                    value = uiState.profileApiUrl,
                    onValueChange = onProfileApiUrlChanged,
                    label = { Text("Profile API URL (volitelne)") },
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
                if (!uiState.info.isNullOrBlank()) {
                    Text(
                        text = uiState.info,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
