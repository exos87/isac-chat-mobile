package sk.uss.isac.chat.mobile.feature.home

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
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sk.uss.isac.chat.mobile.core.data.model.ChatTab
import sk.uss.isac.chat.mobile.core.data.model.ConversationSummary
import sk.uss.isac.chat.mobile.core.data.model.DirectoryUser

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onConversationSelected: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.openConversationEvents.collect { conversationId ->
            onConversationSelected(conversationId)
        }
    }

    HomeScreen(
        uiState = uiState,
        onTabSelected = viewModel::onTabSelected,
        onFilterChanged = viewModel::onFilterChanged,
        onRefresh = { viewModel.refresh() },
        onLogout = viewModel::logout,
        onConversationSelected = onConversationSelected,
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
    onOpenNewConversation: () -> Unit,
    onDismissNewConversation: () -> Unit,
    onNewConversationModeChanged: (NewConversationMode) -> Unit,
    onNewConversationFilterChanged: (String) -> Unit,
    onNewConversationTitleChanged: (String) -> Unit,
    onToggleSubject: (String) -> Unit,
    onCreateConversation: () -> Unit
) {
    val dashboard = uiState.dashboard
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
                Icon(Icons.Outlined.Add, contentDescription = "Novy chat")
            }
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ISAC Chat",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mobilny klient pre widget funkcionalitu",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Obnovit")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = "Odhlasit")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.filter,
                    onValueChange = onFilterChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hladat konverzaciu") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                TabRow(selectedTabIndex = uiState.activeTab.ordinal) {
                    ChatTab.entries.forEach { tab ->
                        val unread = dashboard?.conversations
                            ?.filter { it.belongsTo(tab) }
                            ?.sumOf { it.unreadCount }
                            ?: 0
                        Tab(
                            selected = uiState.activeTab == tab,
                            onClick = { onTabSelected(tab) },
                            text = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(tab.label())
                                    if (unread > 0) {
                                        Badge { Text(unread.toString()) }
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
                            text = "Neprecitane spolu: ${dashboard?.unreadCount ?: 0}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
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
                    if (conversations.isEmpty()) {
                        item {
                            Card {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Zatial nic na zobrazenie", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Pre vybrany tab alebo filter sme nenasli ziadnu konverzaciu.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
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
            Text("Nova konverzacia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TabRow(selectedTabIndex = mode.ordinal) {
                NewConversationMode.entries.forEach { item ->
                    Tab(
                        selected = mode == item,
                        onClick = { onModeChanged(item) },
                        text = { Text(if (item == NewConversationMode.DIRECT) "Direct" else "Skupina") }
                    )
                }
            }
            OutlinedTextField(
                value = filter,
                onValueChange = onFilterChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Najst pracovnika") },
                singleLine = true
            )
            if (mode == NewConversationMode.GROUP) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nazov skupiny") },
                    singleLine = true
                )
            }
            Text(
                text = if (mode == NewConversationMode.DIRECT) {
                    "Vyber jedneho cloveka pre direct chat."
                } else {
                    "Vyber clenov skupiny. Zatial bez teba do requestu neposielame dalsie metadata, backend si session user doplni sam."
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
                    Text(if (mode == NewConversationMode.DIRECT) "Otvorit direct chat" else "Vytvorit skupinu")
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
                    text = conversation.lastMessagePreview ?: "Bez poslednej spravy",
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
