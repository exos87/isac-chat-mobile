package sk.uss.isac.chat.mobile.feature.session

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.R
import sk.uss.isac.chat.mobile.core.ui.UssBlue
import sk.uss.isac.chat.mobile.core.ui.UssBlueDeep
import sk.uss.isac.chat.mobile.core.ui.UssNavy
import sk.uss.isac.chat.mobile.core.notifications.PushDiagnosticsSeverity

@Composable
fun SessionRoute(viewModel: SessionViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
        viewModel.ssoLaunchUrls.collect { url ->
            openUrlInBrowser(context, url)
        }
    }
    LaunchedEffect(
        uiState.baseUrl,
        uiState.wsUrl,
        uiState.oidcAuthUrl,
        uiState.oidcTokenUrl,
        uiState.accessToken,
        uiState.showPasswordLogin,
        uiState.isPreparingSso,
        uiState.isAuthenticating
    ) {
        viewModel.autoStartHostedSsoIfEligible()
    }
    val importTokenLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            viewModel.reportImportError("Výber súboru s tokenom bol zrušený.")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
        }.onSuccess { token ->
            viewModel.importToken(token, "súboru")
        }.onFailure {
            viewModel.reportImportError("Token sa zo súboru nepodarilo načítať.")
        }
    }
    SessionScreen(
        uiState = uiState,
        onBaseUrlChanged = viewModel::onBaseUrlChanged,
        onWsUrlChanged = viewModel::onWsUrlChanged,
        onTokenChanged = viewModel::onTokenChanged,
        onProfileApiUrlChanged = viewModel::onProfileApiUrlChanged,
        onApiTypeChanged = viewModel::onApiTypeChanged,
        onOidcAuthUrlChanged = viewModel::onOidcAuthUrlChanged,
        onOidcTokenUrlChanged = viewModel::onOidcTokenUrlChanged,
        onOidcClientIdChanged = viewModel::onOidcClientIdChanged,
        onOidcRedirectUriChanged = viewModel::onOidcRedirectUriChanged,
        onOidcScopeChanged = viewModel::onOidcScopeChanged,
        onUsernameChanged = viewModel::onUsernameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onApplyLocalPreset = viewModel::applyLocalEmulatorPreset,
        onApplyUseitacPreset = viewModel::applyUseitacPreset,
        onTogglePasswordLogin = viewModel::togglePasswordLogin,
        onToggleAdvancedConfig = viewModel::toggleAdvancedConfig,
        onStartOidcLogin = viewModel::startOidcLogin,
        onAuthenticateViaIsac = viewModel::authenticateViaIsac,
        onTestConnection = viewModel::testSession,
        onLoadPushDiagnostics = viewModel::loadPushDiagnostics,
        onSendPushTest = viewModel::sendPushTest,
        onSyncPushTokenNow = viewModel::syncPushTokenNow,
        onCopyPushReport = {
            val report = uiState.pushReport.orEmpty()
            if (report.isBlank()) {
                viewModel.reportImportError("Najprv nacitaj push diagnostiku alebo zosynchronizuj token.")
            } else {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("push-report", report))
                viewModel.reportInfo("Push report bol skopirovany do clipboardu.")
            }
        },
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
    onOidcAuthUrlChanged: (String) -> Unit,
    onOidcTokenUrlChanged: (String) -> Unit,
    onOidcClientIdChanged: (String) -> Unit,
    onOidcRedirectUriChanged: (String) -> Unit,
    onOidcScopeChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onApplyLocalPreset: () -> Unit,
    onApplyUseitacPreset: () -> Unit,
    onTogglePasswordLogin: () -> Unit,
    onToggleAdvancedConfig: () -> Unit,
    onStartOidcLogin: () -> Unit,
    onAuthenticateViaIsac: () -> Unit,
    onTestConnection: () -> Unit,
    onLoadPushDiagnostics: () -> Unit,
    onSendPushTest: () -> Unit,
    onSyncPushTokenNow: () -> Unit,
    onCopyPushReport: () -> Unit,
    onPasteTokenFromClipboard: () -> Unit,
    onImportTokenFromFile: () -> Unit,
    onSave: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        UssNavy,
                        UssBlueDeep.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    UssNavy,
                                    UssBlueDeep
                                )
                            )
                        )
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ussk_wordmark),
                            contentDescription = BuildConfig.BRAND_NAME,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrandBadge(BuildConfig.BRAND_NAME)
                        BrandBadge(BuildConfig.APP_DISPLAY_TITLE)
                        BrandBadge(BuildConfig.ENVIRONMENT_LABEL)
                    }
                    Text(
                        text = "Mobiln\u00fd chat, schva\u013eovania a skupinov\u00e1 komunik\u00e1cia v jednom firemnom vstupe.",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Odporúčaná cesta je firemné SSO. Ak je to potrebné, nižšie nájdeš aj fallback prihlásenie rovnakým backend flow ako vo web aplikácii.",
                        color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(UssNavy.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = null,
                                tint = UssNavy
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Firemné SSO",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Prihl\u00e1senie cez Keycloak a firemn\u00fd browser v mobile.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Button(
                        onClick = onStartOidcLogin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isPreparingSso && !uiState.isSaving && !uiState.isAuthenticating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UssNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (uiState.isPreparingSso) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Pokračovať cez firemné SSO")
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                    Text(
                        text = "Ak si už v mobilnom prehliadači prihlásený do firemného prostredia, SSO ťa pustí ďalej bez ďalšieho zadávania hesla.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = onTogglePasswordLogin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Key, contentDescription = null)
                            Text(
                                if (uiState.showPasswordLogin) {
                                    "Skry\u0165 fallback prihl\u00e1senie"
                                } else {
                                    "Prihl\u00e1si\u0165 menom a heslom"
                                }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState.showPasswordLogin) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Fallback login",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tento flow používa rovnaký session/BFF login ako web aplikácia a potom si vyžiada upstream bearer token pre chat API.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = uiState.username,
                            onValueChange = onUsernameChanged,
                            label = { Text("Prihlasovacie meno") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = onPasswordChanged,
                            label = { Text("Heslo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        Button(
                            onClick = onAuthenticateViaIsac,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isAuthenticating && !uiState.isSaving && !uiState.isTesting,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            if (uiState.isAuthenticating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Prihl\u00e1si\u0165 a otvori\u0165 chat")
                            }
                        }
                    }
                }
            }

            if (!uiState.info.isNullOrBlank()) {
                StatusCard(
                    message = uiState.info,
                    background = UssBlue.copy(alpha = 0.12f),
                    contentColor = UssNavy
                )
            }

            if (!uiState.error.isNullOrBlank()) {
                StatusCard(
                    message = uiState.error,
                    background = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            }

            OutlinedButton(
                onClick = onToggleAdvancedConfig,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null)
                    Text(
                        if (uiState.showAdvancedConfig) {
                            "Skry\u0165 servisn\u00e9 nastavenia"
                        } else {
                            "Otvori\u0165 servisn\u00e9 nastavenia"
                        }
                    )
                }
            }

            AnimatedVisibility(visible = uiState.showAdvancedConfig) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Servisné nastavenia a endpointy",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tieto hodnoty sú už predvyplnené pre ${BuildConfig.HOSTED_PRESET_LABEL}. Meň ich len pri lokálnom testovaní alebo diagnostike.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onApplyUseitacPreset,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(BuildConfig.HOSTED_PRESET_LABEL)
                            }
                            OutlinedButton(
                                onClick = onApplyLocalPreset,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Lok\u00e1lny Docker")
                            }
                        }
                        OutlinedButton(
                            onClick = onTestConnection,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isTesting && !uiState.isSaving,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (uiState.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Otestova\u0165 spojenie s backendom")
                            }
                        }
                        if (uiState.hasStoredSession) {
                            Text(
                                text = "Push diagnostika",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onLoadPushDiagnostics,
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isLoadingPushDiagnostics && !uiState.isSendingPushTest && !uiState.isSyncingPushToken,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    if (uiState.isLoadingPushDiagnostics) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.height(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Načítať stav push")
                                    }
                                }
                                OutlinedButton(
                                    onClick = onSendPushTest,
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isLoadingPushDiagnostics && !uiState.isSendingPushTest && !uiState.isSyncingPushToken,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    if (uiState.isSendingPushTest) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.height(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Poslať test push")
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = onSyncPushTokenNow,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isLoadingPushDiagnostics && !uiState.isSendingPushTest && !uiState.isSyncingPushToken,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (uiState.isSyncingPushToken) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Znova zosynchronizovať push token")
                                }
                            }
                            OutlinedButton(
                                onClick = onCopyPushReport,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.pushReport.isNullOrBlank(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Skopírovať push report")
                            }
                            PushAssessmentCard(
                                title = uiState.pushAssessmentTitle,
                                message = uiState.pushAssessmentMessage,
                                severity = uiState.pushAssessmentSeverity
                            )
                            StatusCard(
                                message = uiState.pushDiagnosticsSummary,
                                background = UssBlue.copy(alpha = 0.08f),
                                contentColor = UssNavy
                            )
                            StatusCard(
                                message = uiState.localPushSummary,
                                background = UssBlue.copy(alpha = 0.06f),
                                contentColor = UssNavy
                            )
                            StatusCard(
                                message = uiState.pushConsistencySummary,
                                background = UssBlue.copy(alpha = 0.06f),
                                contentColor = UssNavy
                            )
                            StatusCard(
                                message = uiState.pushTokenSyncSummary,
                                background = UssBlue.copy(alpha = 0.06f),
                                contentColor = UssNavy
                            )
                            StatusCard(
                                message = uiState.pushTestSummary,
                                background = UssBlue.copy(alpha = 0.06f),
                                contentColor = UssNavy
                            )
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
                            value = uiState.profileApiUrl,
                            onValueChange = onProfileApiUrlChanged,
                            label = { Text("Profile API URL") },
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
                            value = uiState.oidcAuthUrl,
                            onValueChange = onOidcAuthUrlChanged,
                            label = { Text("OIDC Authorization URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.oidcTokenUrl,
                            onValueChange = onOidcTokenUrlChanged,
                            label = { Text("OIDC Token URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.oidcClientId,
                            onValueChange = onOidcClientIdChanged,
                            label = { Text("OIDC Client ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.oidcRedirectUri,
                            onValueChange = onOidcRedirectUriChanged,
                            label = { Text("OIDC Redirect URI") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.oidcScope,
                            onValueChange = onOidcScopeChanged,
                            label = { Text("OIDC Scope") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text(
                            text = "Fallback pre lokálne testy: manuálny bearer token.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (uiState.manualBearerBootstrapEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onPasteTokenFromClipboard,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Token z clipboardu")
                                }
                                OutlinedButton(
                                    onClick = onImportTokenFromFile,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Token zo s\u00faboru")
                                }
                            }
                        } else {
                            Text(
                                text = "Ručný bearer bootstrap je v tejto verzii vypnutý. Pre prihlásenie použi firemné SSO alebo fallback login cez meno a heslo.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (uiState.manualBearerBootstrapEnabled) {
                            OutlinedTextField(
                                value = uiState.accessToken,
                                onValueChange = onTokenChanged,
                                label = { Text("Bearer token") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 5,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                            Button(
                                onClick = onSave,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isSaving && !uiState.isAuthenticating,
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                if (uiState.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Pou\u017ei\u0165 existuj\u00faci bearer token")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = BuildConfig.APP_FOOTER_LABEL,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.80f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BrandBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusCard(
    message: String?,
    background: Color,
    contentColor: Color
) {
    if (message.isNullOrBlank()) {
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PushAssessmentCard(
    title: String?,
    message: String?,
    severity: PushDiagnosticsSeverity
) {
    if (title.isNullOrBlank() || message.isNullOrBlank()) {
        return
    }
    val (background, contentColor) = when (severity) {
        PushDiagnosticsSeverity.OK -> UssBlue.copy(alpha = 0.10f) to UssNavy
        PushDiagnosticsSeverity.WARNING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f) to MaterialTheme.colorScheme.tertiary
        PushDiagnosticsSeverity.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f) to MaterialTheme.colorScheme.error
        PushDiagnosticsSeverity.UNKNOWN -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f) to MaterialTheme.colorScheme.secondary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun openUrlInBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    val defaultBrowserPackage = Intent(Intent.ACTION_VIEW, Uri.parse("https://useitac.onesoft.sk"))
        .resolveActivity(context.packageManager)
        ?.packageName
    runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(AndroidColor.parseColor("#16365C"))
                    .build()
            )
            .build()
            .also { intent ->
                if (!defaultBrowserPackage.isNullOrBlank()) {
                    intent.intent.setPackage(defaultBrowserPackage)
                }
            }
            .launchUrl(context, uri)
    }.getOrElse {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                if (!defaultBrowserPackage.isNullOrBlank()) {
                    `package` = defaultBrowserPackage
                }
            }
        )
    }
}
