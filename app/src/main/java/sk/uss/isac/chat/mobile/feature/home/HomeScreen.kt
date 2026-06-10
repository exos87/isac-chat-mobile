package sk.uss.isac.chat.mobile.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.R
import sk.uss.isac.chat.mobile.core.data.model.ApprovalCase
import sk.uss.isac.chat.mobile.core.data.model.ChatTab
import sk.uss.isac.chat.mobile.core.data.model.ConversationSummary
import sk.uss.isac.chat.mobile.core.data.model.DirectoryUser
import sk.uss.isac.chat.mobile.core.ui.UssBlue
import sk.uss.isac.chat.mobile.core.ui.UssBlueDeep
import sk.uss.isac.chat.mobile.core.ui.UssNavy

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenConversationRequest: (HomeOpenConversationRequest) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.openConversationEvents.collect { request ->
            onOpenConversationRequest(request)
        }
    }

    HomeScreen(
        uiState = uiState,
        onTabSelected = viewModel::onTabSelected,
        onFilterChanged = viewModel::onFilterChanged,
        onRefresh = { viewModel.refresh() },
        onLogout = viewModel::logout,
        onConversationSelected = viewModel::openConversation,
        onApprovalSelected = viewModel::openPendingApproval,
        onOpenNewConversation = viewModel::openNewConversationSheet,
        onDismissNewConversation = viewModel::dismissNewConversationSheet,
        onNewConversationModeChanged = viewModel::onNewConversationModeChanged,
        onNewConversationFilterChanged = viewModel::onNewConversationFilterChanged,
        onNewConversationTitleChanged = viewModel::onNewConversationTitleChanged,
        onToggleSubject = viewModel::toggleSelectedSubject,
        onCreateConversation = viewModel::createConversation
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onTabSelected: (ChatTab) -> Unit,
    onFilterChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onConversationSelected: (Long) -> Unit,
    onApprovalSelected: (ApprovalCase) -> Unit,
    onOpenNewConversation: () -> Unit,
    onDismissNewConversation: () -> Unit,
    onNewConversationModeChanged: (NewConversationMode) -> Unit,
    onNewConversationFilterChanged: (String) -> Unit,
    onNewConversationTitleChanged: (String) -> Unit,
    onToggleSubject: (String) -> Unit,
    onCreateConversation: () -> Unit
) {
    val dashboard = uiState.dashboard
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    val lastSyncLabel = uiState.lastSuccessfulSyncAtEpochMillis?.let(::formatLastSyncLabel)
    val conversations = dashboard
        ?.conversations
        ?.filter { it.belongsTo(uiState.activeTab) }
        ?.filter { conversation ->
            val query = uiState.filter.trim()
            if (query.isBlank()) {
                true
            } else {
                conversation.title.contains(query, ignoreCase = true) ||
                    conversation.lastMessagePreview.orEmpty().contains(query, ignoreCase = true) ||
                    conversation.externalReference.orEmpty().contains(query, ignoreCase = true)
            }
        }
        .orEmpty()
    val pendingApprovals = uiState.pendingApprovals
        .filter { approval ->
            val query = uiState.filter.trim()
            if (uiState.activeTab != ChatTab.ACTIONS) {
                false
            } else if (query.isBlank()) {
                true
            } else {
                approval.proposalCode.orEmpty().contains(query, ignoreCase = true) ||
                    approval.proposalText.orEmpty().contains(query, ignoreCase = true) ||
                    approval.requestedBySubject.orEmpty().contains(query, ignoreCase = true)
            }
        }

    val directoryUsers = dashboard
        ?.directory
        ?.filter { user ->
            val query = uiState.newConversationFilter.trim()
            if (query.isBlank()) {
                true
            } else {
                user.displayName.contains(query, ignoreCase = true) ||
                    user.email.orEmpty().contains(query, ignoreCase = true) ||
                    user.userName.orEmpty().contains(query, ignoreCase = true)
            }
        }
        .orEmpty()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenNewConversation) {
                Icon(Icons.Outlined.Add, contentDescription = "Nov\u00fd chat")
            }
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(UssNavy, UssBlueDeep)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ussk_wordmark),
                                contentDescription = BuildConfig.BRAND_NAME,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = BuildConfig.APP_DISPLAY_TITLE,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = BuildConfig.APP_SUPPORTING_LABEL,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Obnovi\u0165",
                            tint = Color.White
                        )
                    }
                    OutlinedButton(
                        onClick = { showLogoutConfirmation = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ExitToApp,
                            contentDescription = "Odhlásiť sa",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Odhlásiť sa")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.filter,
                    onValueChange = onFilterChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("H\u013eada\u0165 konverz\u00e1ciu") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                TabRow(
                    selectedTabIndex = uiState.activeTab.ordinal,
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White
                ) {
                    ChatTab.entries.forEach { tab ->
                        val unread = dashboard?.conversations
                            ?.filter { it.belongsTo(tab) }
                            ?.sumOf { it.unreadCount }
                            ?: 0
                        val badgeCount = if (tab == ChatTab.ACTIONS) {
                            unread + uiState.pendingApprovals.size
                        } else {
                            unread
                        }
                        Tab(
                            selected = uiState.activeTab == tab,
                            onClick = { onTabSelected(tab) },
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(tab.label(), color = Color.White)
                                    if (badgeCount > 0) {
                                        Badge(
                                            containerColor = UssBlue,
                                            contentColor = Color.White
                                        ) { Text(badgeCount.toString()) }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && conversations.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.error, color = MaterialTheme.colorScheme.error)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Neprečítané spolu: ${dashboard?.unreadCount ?: 0}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (uiState.isRealtimeConnected == false || uiState.isUsingCachedDashboard || uiState.connectivityMessage != null) {
                        item {
                            Card {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = if (uiState.isUsingCachedDashboard) {
                                            "Zobrazené sú posledné načítané dáta"
                                        } else {
                                            "Spojenie je momentálne nestabilné"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (uiState.isUsingCachedDashboard) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = uiState.connectivityMessage
                                            ?: "Zoznam konverzácií sa môže obnoviť s oneskorením. Po návrate spojenia sa dashboard zosynchronizuje automaticky.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (lastSyncLabel != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Posledná úspešná synchronizácia: $lastSyncLabel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (uiState.error != null) {
                        item {
                            Text(
                                text = uiState.error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if (pendingApprovals.isNotEmpty()) {
                        item {
                            PendingApprovalsSection(
                                approvals = pendingApprovals,
                                onApprovalSelected = onApprovalSelected
                            )
                        }
                    }
                    if (conversations.isEmpty() && pendingApprovals.isEmpty()) {
                        item {
                            Card {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Zatia\u013e ni\u010d na zobrazenie", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Pre vybran\u00fd tab alebo filter sme nena\u0161li \u017eiadnu konverz\u00e1ciu.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (conversations.isNotEmpty()) {
                        items(conversations, key = { it.id }) { conversation ->
                            ConversationListItem(
                                conversation = conversation,
                                onClick = { onConversationSelected(conversation.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showNewConversationSheet) {
        NewConversationSheet(
            mode = uiState.newConversationMode,
            filter = uiState.newConversationFilter,
            title = uiState.newConversationTitle,
            selectedSubjects = uiState.selectedSubjects,
            isCreating = uiState.isCreatingConversation,
            users = directoryUsers,
            onDismiss = onDismissNewConversation,
            onModeChanged = onNewConversationModeChanged,
            onFilterChanged = onNewConversationFilterChanged,
            onTitleChanged = onNewConversationTitleChanged,
            onToggleSubject = onToggleSubject,
            onCreate = onCreateConversation
        )
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Odhlásiť sa?") },
            text = { Text("Po potvrdení sa ukončí aktuálna session a appka sa vráti na prihlasovaciu obrazovku.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmation = false
                        onLogout()
                    }
                ) {
                    Text("Odhlásiť")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) {
                    Text("Zrušiť")
                }
            }
        )
    }
}

private fun formatLastSyncLabel(timestamp: Long): String {
    return DateTimeFormatter
        .ofPattern("d. M. yyyy HH:mm", Locale("sk", "SK"))
        .format(
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        )
}

private fun formatApprovalDateLabel(value: String): String {
    return runCatching {
        DateTimeFormatter
            .ofPattern("d. M. HH:mm", Locale("sk", "SK"))
            .format(
                Instant.parse(value)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
            )
    }.getOrDefault(value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewConversationSheet(
    mode: NewConversationMode,
    filter: String,
    title: String,
    selectedSubjects: Set<String>,
    isCreating: Boolean,
    users: List<DirectoryUser>,
    onDismiss: () -> Unit,
    onModeChanged: (NewConversationMode) -> Unit,
    onFilterChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onToggleSubject: (String) -> Unit,
    onCreate: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nov\u00e1 konverz\u00e1cia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TabRow(selectedTabIndex = mode.ordinal) {
                NewConversationMode.entries.forEach { item ->
                    Tab(
                        selected = mode == item,
                        onClick = { onModeChanged(item) },
                        text = { Text(if (item == NewConversationMode.DIRECT) "Priamy chat" else "Skupina") }
                    )
                }
            }
            OutlinedTextField(
                value = filter,
                onValueChange = onFilterChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("N\u00e1js\u0165 pracovn\u00edka") },
                singleLine = true
            )
            if (mode == NewConversationMode.GROUP) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("N\u00e1zov skupiny") },
                    singleLine = true
                )
            }
            Text(
                text = if (mode == NewConversationMode.DIRECT) {
                    "Vyber jedn\u00e9ho \u010dloveka pre priamy chat."
                } else {
                    "Vyber \u010dlenov skupiny. Zatia\u013e bez teba do requestu neposielame \u010fal\u0161ie metad\u00e1ta, backend si session user dopln\u00ed s\u00e1m."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(users, key = { it.subject }) { user ->
                    DirectoryUserItem(
                        user = user,
                        selected = selectedSubjects.contains(user.subject),
                        onClick = { onToggleSubject(user.subject) }
                    )
                }
            }
            Button(
                onClick = onCreate,
                enabled = !isCreating && selectedSubjects.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (mode == NewConversationMode.DIRECT) "Otvori\u0165 priamy chat" else "Vytvori\u0165 skupinu")
                }
            }
        }
    }
}

@Composable
private fun DirectoryUserItem(
    user: DirectoryUser,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(user.displayName.take(2).uppercase(), color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    text = user.email ?: user.userName ?: user.subject,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (user.online) {
                Badge { Text("ON") }
            }
        }
    }
}

@Composable
private fun PendingApprovalsSection(
    approvals: List<ApprovalCase>,
    onApprovalSelected: (ApprovalCase) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Moje čakajúce akcie (${approvals.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Rozhodnutia, kde ste kompetentný používateľ. Ťuknutím otvoríte príslušnú konverzáciu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            approvals.forEach { approval ->
                PendingApprovalItem(
                    approval = approval,
                    onClick = { onApprovalSelected(approval) }
                )
            }
        }
    }
}

@Composable
private fun PendingApprovalItem(
    approval: ApprovalCase,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = approval.proposalCode?.takeIf { it.isNotBlank() } ?: "Schválenie #${approval.id}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Badge(
                    containerColor = UssBlue,
                    contentColor = Color.White
                ) { Text("Čaká") }
            }
            Text(
                text = approval.proposalText?.takeIf { it.isNotBlank() }
                    ?: "Otvorte konverzáciu a rozhodnite o ďalšom postupe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Konverzácia #${approval.conversationId}"
                    + (approval.requestedAt?.let { " • ${formatApprovalDateLabel(it)}" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun ConversationListItem(
    conversation: ConversationSummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.title.take(2).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (conversation.primarySubject != null) {
                Spacer(modifier = Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (conversation.online) Color(0xFF2E7D32) else Color(0xFFB0BEC5))
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = conversation.lastMessagePreview ?: "Bez poslednej spr\u00e1vy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!conversation.externalReference.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = conversation.externalReference,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (conversation.unreadCount > 0) {
                Badge(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Text(conversation.unreadCount.toString())
                }
            }
        }
    }
}

private fun ChatTab.label(): String = when (this) {
    ChatTab.CHAT -> "Chaty"
    ChatTab.GROUPS -> "Skupiny"
    ChatTab.ACTIONS -> "Akcie"
}
