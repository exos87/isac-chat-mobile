package sk.uss.isac.chat.mobile.feature.conversation

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sk.uss.isac.chat.mobile.core.data.model.ApprovalCase
import sk.uss.isac.chat.mobile.core.data.model.ApprovalDecisionCode
import sk.uss.isac.chat.mobile.core.data.model.ApprovalStatus
import sk.uss.isac.chat.mobile.core.data.model.ChatAttachment
import sk.uss.isac.chat.mobile.core.data.model.ChatMessage
import sk.uss.isac.chat.mobile.core.data.model.ConversationBundle
import sk.uss.isac.chat.mobile.core.data.model.ConversationMember
import sk.uss.isac.chat.mobile.core.data.model.ConversationType
import sk.uss.isac.chat.mobile.core.data.model.DirectoryUser
import sk.uss.isac.chat.mobile.core.data.model.LocalAttachmentDraft
import sk.uss.isac.chat.mobile.core.data.model.MemberRole
import sk.uss.isac.chat.mobile.core.data.model.MessageType
import sk.uss.isac.chat.mobile.core.data.model.VisibilityScope

@Composable
fun ConversationRoute(
    viewModel: ConversationViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPendingAttachments(uris.mapNotNull { uri ->
                context.contentResolver.takePersistableReadPermissionSafely(uri)
                uri.toLocalAttachmentDraft(context)
            })
        }
    }

    LaunchedEffect(uiState.hasLeftConversation) {
        if (uiState.hasLeftConversation) {
            onBack()
        }
    }

    LaunchedEffect(uiState.downloadedAttachment) {
        val downloaded = uiState.downloadedAttachment ?: return@LaunchedEffect
        val file = File(downloaded.filePath)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val openIntent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, downloaded.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(openIntent, downloaded.fileName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(chooser) }
                .recoverCatching {
                    context.startActivity(
                        Intent.createChooser(
                            openIntent.setDataAndType(uri, "*/*"),
                            downloaded.fileName
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
        }
        viewModel.consumeDownloadedAttachment()
    }

    ConversationScreen(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onPaneSelected = viewModel::onPaneSelected,
        onComposerTextChanged = viewModel::onComposerTextChanged,
        onPickAttachments = { attachmentPicker.launch(arrayOf("*/*")) },
        onRemovePendingAttachment = viewModel::removePendingAttachment,
        onVisibilityScopeChanged = viewModel::onVisibilityScopeChanged,
        onSendMessage = viewModel::sendMessage,
        onApprovalMessageSelected = viewModel::onApprovalMessageSelected,
        onApprovalCompetentChanged = viewModel::onApprovalCompetentChanged,
        onApprovalProposalCodeChanged = viewModel::onApprovalProposalCodeChanged,
        onApprovalProposalTextChanged = viewModel::onApprovalProposalTextChanged,
        onSubmitApproval = viewModel::submitApprovalRequest,
        onClearApprovalDraft = viewModel::clearApprovalDraft,
        onGroupTitleChanged = viewModel::onGroupTitleChanged,
        onNewMemberSelected = viewModel::onNewMemberSelected,
        onRenameGroup = viewModel::renameGroup,
        onAddMember = viewModel::addSelectedMember,
        onUpdateRole = viewModel::updateGroupMemberRole,
        onRemoveMember = viewModel::removeGroupMember,
        onLeaveConversation = viewModel::leaveConversation,
        onOpenAttachment = viewModel::openAttachment,
        onDeleteAttachment = viewModel::deleteAttachment,
        onDeleteMessage = viewModel::deleteMessage,
        onDecision = viewModel::decideApproval
    )
}

@Composable
fun ConversationScreen(
    uiState: ConversationUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPaneSelected: (ConversationPane) -> Unit,
    onComposerTextChanged: (String) -> Unit,
    onPickAttachments: () -> Unit,
    onRemovePendingAttachment: (String) -> Unit,
    onVisibilityScopeChanged: (VisibilityScope) -> Unit,
    onSendMessage: () -> Unit,
    onApprovalMessageSelected: (Long) -> Unit,
    onApprovalCompetentChanged: (String) -> Unit,
    onApprovalProposalCodeChanged: (String) -> Unit,
    onApprovalProposalTextChanged: (String) -> Unit,
    onSubmitApproval: () -> Unit,
    onClearApprovalDraft: () -> Unit,
    onGroupTitleChanged: (String) -> Unit,
    onNewMemberSelected: (String) -> Unit,
    onRenameGroup: () -> Unit,
    onAddMember: () -> Unit,
    onUpdateRole: (Long, MemberRole) -> Unit,
    onRemoveMember: (Long) -> Unit,
    onLeaveConversation: () -> Unit,
    onOpenAttachment: (Long) -> Unit,
    onDeleteAttachment: (Long) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onDecision: (Long, ApprovalDecisionCode) -> Unit
) {
    val bundle = uiState.bundle
    val panes = conversationPanes(bundle)

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Spat")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bundle?.conversation?.title ?: "Konverzacia",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildHeaderSubtitle(bundle),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Obnovit")
                    }
                }
                if (panes.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TabRow(selectedTabIndex = panes.indexOf(uiState.activePane).coerceAtLeast(0)) {
                        panes.forEach { pane ->
                            Tab(
                                selected = uiState.activePane == pane,
                                onClick = { onPaneSelected(pane) },
                                text = { Text(pane.label()) }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (uiState.activePane == ConversationPane.MESSAGES && bundle != null) {
                ComposerBar(
                    text = uiState.composerText,
                    pendingAttachments = uiState.pendingAttachments,
                    visibilityScope = uiState.visibilityScope,
                    isSending = uiState.isSending,
                    showVisibilityScope = bundle.conversation.fixedGroup,
                    onTextChanged = onComposerTextChanged,
                    onPickAttachments = onPickAttachments,
                    onRemovePendingAttachment = onRemovePendingAttachment,
                    onVisibilityScopeChanged = onVisibilityScopeChanged,
                    onSend = onSendMessage
                )
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

            bundle == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.error ?: "Konverzacia nie je dostupna.")
                }
            }

            uiState.activePane == ConversationPane.ACTIONS -> {
                ApprovalPane(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    uiState = uiState,
                    bundle = bundle,
                    onApprovalMessageSelected = onApprovalMessageSelected,
                    onApprovalCompetentChanged = onApprovalCompetentChanged,
                    onApprovalProposalCodeChanged = onApprovalProposalCodeChanged,
                    onApprovalProposalTextChanged = onApprovalProposalTextChanged,
                    onSubmitApproval = onSubmitApproval,
                    onClearApprovalDraft = onClearApprovalDraft,
                    onDecision = onDecision
                )
            }

            uiState.activePane == ConversationPane.GROUP -> {
                GroupManagementPane(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    uiState = uiState,
                    bundle = bundle,
                    onGroupTitleChanged = onGroupTitleChanged,
                    onNewMemberSelected = onNewMemberSelected,
                    onRenameGroup = onRenameGroup,
                    onAddMember = onAddMember,
                    onUpdateRole = onUpdateRole,
                    onRemoveMember = onRemoveMember,
                    onLeaveConversation = onLeaveConversation
                )
            }

            else -> {
                MessagePane(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    uiState = uiState,
                    bundle = bundle,
                    onApprovalMessageSelected = onApprovalMessageSelected,
                    onOpenAttachment = onOpenAttachment,
                    onDeleteAttachment = onDeleteAttachment,
                    onDeleteMessage = onDeleteMessage
                )
            }
        }
    }
}

@Composable
private fun MessagePane(
    modifier: Modifier,
    uiState: ConversationUiState,
    bundle: ConversationBundle,
    onApprovalMessageSelected: (Long) -> Unit,
    onOpenAttachment: (Long) -> Unit,
    onDeleteAttachment: (Long) -> Unit,
    onDeleteMessage: (Long) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MemberChipRow(bundle)
        }
        if (uiState.error != null) {
            item {
                Text(uiState.error, color = MaterialTheme.colorScheme.error)
            }
        }
        items(bundle.messages, key = { it.id }) { message ->
            val canRequestApproval = bundle.conversation.approvalEnabled &&
                uiState.currentSubject == message.senderSubject &&
                !message.deleted &&
                message.messageType == MessageType.USER_MESSAGE

            MessageCard(
                message = message,
                attachments = bundle.attachmentsByMessageId[message.id].orEmpty(),
                currentSubject = uiState.currentSubject,
                canRequestApproval = canRequestApproval,
                isDeleting = uiState.deletingMessageId == message.id,
                openingAttachmentId = uiState.openingAttachmentId,
                deletingAttachmentId = uiState.deletingAttachmentId,
                onRequestApproval = { onApprovalMessageSelected(message.id) },
                onOpenAttachment = onOpenAttachment,
                onDeleteAttachment = onDeleteAttachment,
                onDeleteMessage = { onDeleteMessage(message.id) }
            )
        }
    }
}

@Composable
private fun ApprovalPane(
    modifier: Modifier,
    uiState: ConversationUiState,
    bundle: ConversationBundle,
    onApprovalMessageSelected: (Long) -> Unit,
    onApprovalCompetentChanged: (String) -> Unit,
    onApprovalProposalCodeChanged: (String) -> Unit,
    onApprovalProposalTextChanged: (String) -> Unit,
    onSubmitApproval: () -> Unit,
    onClearApprovalDraft: () -> Unit,
    onDecision: (Long, ApprovalDecisionCode) -> Unit
) {
    val selectableMessages = bundle.messages.filter { message ->
        message.senderSubject == uiState.currentSubject &&
            !message.deleted &&
            message.messageType == MessageType.USER_MESSAGE
    }
    val selectedMessage = selectableMessages.firstOrNull { it.id == uiState.selectedApprovalMessageId }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.error != null) {
            item {
                Text(uiState.error, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            ApprovalRequestCard(
                selectableMessages = selectableMessages,
                selectedMessageId = uiState.selectedApprovalMessageId,
                selectedMessage = selectedMessage,
                approvalCandidates = uiState.approvalCandidates,
                selectedCompetentSubject = uiState.selectedCompetentSubject,
                proposalCode = uiState.approvalProposalCode,
                proposalText = uiState.approvalProposalText,
                isSubmitting = uiState.isSubmittingApproval,
                onApprovalMessageSelected = onApprovalMessageSelected,
                onApprovalCompetentChanged = onApprovalCompetentChanged,
                onApprovalProposalCodeChanged = onApprovalProposalCodeChanged,
                onApprovalProposalTextChanged = onApprovalProposalTextChanged,
                onSubmitApproval = onSubmitApproval,
                onClearApprovalDraft = onClearApprovalDraft
            )
        }
        if (bundle.approvals.isEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Zatial bez schvalovacich pripadov", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Po odoslani requestu sa tu zobrazi historia approval case-ov rovnako ako vo widgete.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(bundle.approvals, key = { it.id }) { approval ->
                ApprovalCard(
                    approval = approval,
                    currentSubject = uiState.currentSubject,
                    onDecision = onDecision
                )
            }
        }
    }
}

@Composable
private fun GroupManagementPane(
    modifier: Modifier,
    uiState: ConversationUiState,
    bundle: ConversationBundle,
    onGroupTitleChanged: (String) -> Unit,
    onNewMemberSelected: (String) -> Unit,
    onRenameGroup: () -> Unit,
    onAddMember: () -> Unit,
    onUpdateRole: (Long, MemberRole) -> Unit,
    onRemoveMember: (Long) -> Unit,
    onLeaveConversation: () -> Unit
) {
    val currentMember = bundle.conversation.members.firstOrNull { it.userSubject == uiState.currentSubject }
    val canManageMembers = currentMember?.canManageMembers == true
    val isOwner = currentMember?.memberRole == MemberRole.OWNER
    val existingSubjects = bundle.conversation.members.map { it.userSubject }.toSet()
    val availableDirectoryUsers = uiState.directoryUsers.filter { user ->
        user.subject.isNotBlank() && user.subject !in existingSubjects
    }
    val selectedNewMember = availableDirectoryUsers.firstOrNull { it.subject == uiState.selectedNewMemberSubject }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.error != null) {
            item {
                Text(uiState.error, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.ManageAccounts, contentDescription = null)
                        Text("Sprava skupiny", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Open group management premenovanie, clenovia, role a odchod zo skupiny.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (canManageMembers) {
            item {
                RenameGroupCard(
                    title = uiState.groupTitleDraft,
                    isSaving = uiState.isRenamingGroup,
                    onTitleChanged = onGroupTitleChanged,
                    onRenameGroup = onRenameGroup
                )
            }
            item {
                AddMemberCard(
                    availableUsers = availableDirectoryUsers,
                    selectedUser = selectedNewMember,
                    selectedSubject = uiState.selectedNewMemberSubject,
                    isAdding = uiState.isAddingGroupMember,
                    onNewMemberSelected = onNewMemberSelected,
                    onAddMember = onAddMember
                )
            }
        }
        item {
            MemberManagementCard(
                members = bundle.conversation.members,
                currentSubject = uiState.currentSubject,
                currentMemberRole = currentMember?.memberRole,
                canManageMembers = canManageMembers,
                isOwner = isOwner,
                updatingMemberId = uiState.updatingMemberId,
                removingMemberId = uiState.removingMemberId,
                onUpdateRole = onUpdateRole,
                onRemoveMember = onRemoveMember
            )
        }
        item {
            LeaveConversationCard(
                isLeaving = uiState.isLeavingConversation,
                onLeaveConversation = onLeaveConversation
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApprovalRequestCard(
    selectableMessages: List<ChatMessage>,
    selectedMessageId: Long?,
    selectedMessage: ChatMessage?,
    approvalCandidates: List<DirectoryUser>,
    selectedCompetentSubject: String,
    proposalCode: String,
    proposalText: String,
    isSubmitting: Boolean,
    onApprovalMessageSelected: (Long) -> Unit,
    onApprovalCompetentChanged: (String) -> Unit,
    onApprovalProposalCodeChanged: (String) -> Unit,
    onApprovalProposalTextChanged: (String) -> Unit,
    onSubmitApproval: () -> Unit,
    onClearApprovalDraft: () -> Unit
) {
    var targetExpanded by remember { mutableStateOf(false) }
    var competentExpanded by remember { mutableStateOf(false) }
    val selectedCompetent = approvalCandidates.firstOrNull { it.subject == selectedCompetentSubject }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nova akcia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (selectableMessages.isEmpty()) {
                Text(
                    "V tejto konverzacii zatial nemas vlastnu spravu, nad ktorou sa da zalozit schvalenie.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            ExposedDropdownMenuBox(
                expanded = targetExpanded,
                onExpandedChange = { targetExpanded = !targetExpanded }
            ) {
                OutlinedTextField(
                    value = selectedMessage?.body?.take(80).orEmpty(),
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    label = { Text("Cielova sprava") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = targetExpanded,
                    onDismissRequest = { targetExpanded = false }
                ) {
                    selectableMessages.forEach { message ->
                        DropdownMenuItem(
                            text = { Text(message.body.take(80)) },
                            onClick = {
                                onApprovalMessageSelected(message.id)
                                targetExpanded = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = competentExpanded,
                onExpandedChange = { competentExpanded = !competentExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCompetent?.displayName.orEmpty(),
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    label = { Text("Kompetentny") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = competentExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = competentExpanded,
                    onDismissRequest = { competentExpanded = false }
                ) {
                    approvalCandidates.forEach { user ->
                        DropdownMenuItem(
                            text = { Text(user.displayName) },
                            onClick = {
                                onApprovalCompetentChanged(user.subject)
                                competentExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = proposalCode,
                onValueChange = onApprovalProposalCodeChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Kod postupu") },
                singleLine = true
            )

            OutlinedTextField(
                value = proposalText,
                onValueChange = onApprovalProposalTextChanged,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("Navrh dalsieho postupu") }
            )

            if (selectedMessage != null) {
                Text(
                    text = "Vybrana sprava: ${selectedMessage.body.take(180)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSubmitApproval,
                    enabled = !isSubmitting && selectedMessageId != null && selectedCompetentSubject.isNotBlank()
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Zalozit schvalenie")
                    }
                }
                OutlinedButton(
                    onClick = onClearApprovalDraft,
                    enabled = !isSubmitting
                ) {
                    Text("Zrusit vyber")
                }
            }
        }
    }
}

@Composable
private fun RenameGroupCard(
    title: String,
    isSaving: Boolean,
    onTitleChanged: (String) -> Unit,
    onRenameGroup: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Premenovat skupinu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nazov skupiny") },
                singleLine = true
            )
            Button(onClick = onRenameGroup, enabled = !isSaving && title.isNotBlank()) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Ulozit nazov")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberCard(
    availableUsers: List<DirectoryUser>,
    selectedUser: DirectoryUser?,
    selectedSubject: String,
    isAdding: Boolean,
    onNewMemberSelected: (String) -> Unit,
    onAddMember: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.GroupAdd, contentDescription = null)
                Text("Pridat clena", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (availableUsers.isEmpty()) {
                Text(
                    "Vsetci dostupni pouzivatelia uz su v skupine.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedUser?.displayName.orEmpty(),
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        label = { Text("Novy clen") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableUsers.forEach { user ->
                            DropdownMenuItem(
                                text = { Text(user.displayName) },
                                onClick = {
                                    onNewMemberSelected(user.subject)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Button(
                    onClick = onAddMember,
                    enabled = !isAdding && (selectedSubject.isNotBlank() || availableUsers.isNotEmpty())
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Pridat do skupiny")
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberManagementCard(
    members: List<ConversationMember>,
    currentSubject: String?,
    currentMemberRole: MemberRole?,
    canManageMembers: Boolean,
    isOwner: Boolean,
    updatingMemberId: Long?,
    removingMemberId: Long?,
    onUpdateRole: (Long, MemberRole) -> Unit,
    onRemoveMember: (Long) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Clenovia skupiny", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            members.forEach { member ->
                GroupMemberRow(
                    member = member,
                    isCurrentUser = member.userSubject == currentSubject,
                    currentMemberRole = currentMemberRole,
                    canManageMembers = canManageMembers,
                    isOwner = isOwner,
                    isUpdating = updatingMemberId == member.id,
                    isRemoving = removingMemberId == member.id,
                    onUpdateRole = onUpdateRole,
                    onRemoveMember = onRemoveMember
                )
            }
        }
    }
}

@Composable
private fun GroupMemberRow(
    member: ConversationMember,
    isCurrentUser: Boolean,
    currentMemberRole: MemberRole?,
    canManageMembers: Boolean,
    isOwner: Boolean,
    isUpdating: Boolean,
    isRemoving: Boolean,
    onUpdateRole: (Long, MemberRole) -> Unit,
    onRemoveMember: (Long) -> Unit
) {
    val canPromoteOrDemote = isOwner && !isCurrentUser && member.memberRole != MemberRole.OWNER
    val canRemove = canManageMembers &&
        !isCurrentUser &&
        member.memberRole != MemberRole.OWNER &&
        !(currentMemberRole == MemberRole.ADMIN && member.memberRole == MemberRole.ADMIN)
    val targetRole = if (member.memberRole == MemberRole.ADMIN) MemberRole.MEMBER else MemberRole.ADMIN

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = buildString {
                    append(member.displayName)
                    if (isCurrentUser) {
                        append(" (ty)")
                    }
                },
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Rola: ${member.memberRole.name} | ${if (member.online) "online" else "offline"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canPromoteOrDemote || canRemove) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canPromoteOrDemote) {
                        Button(
                            onClick = { onUpdateRole(member.id, targetRole) },
                            enabled = !isUpdating && !isRemoving
                        ) {
                            if (isUpdating) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (targetRole == MemberRole.ADMIN) "Spravit adminom" else "Spravit clenom")
                            }
                        }
                    }
                    if (canRemove) {
                        OutlinedButton(
                            onClick = { onRemoveMember(member.id) },
                            enabled = !isRemoving && !isUpdating
                        ) {
                            if (isRemoving) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Odobrat")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveConversationCard(
    isLeaving: Boolean,
    onLeaveConversation: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = null)
                Text("Opustit skupinu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Ak si vlastnik, backend automaticky priradi noveho vlastnika podla pravidiel skupiny.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onLeaveConversation, enabled = !isLeaving) {
                if (isLeaving) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Opustit konverzaciu")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberChipRow(bundle: ConversationBundle) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        bundle.conversation.members.forEach { member ->
            AssistChip(
                onClick = {},
                label = {
                    Text("${member.displayName} | ${member.memberRole.name}")
                }
            )
        }
    }
}

@Composable
private fun MessageCard(
    message: ChatMessage,
    attachments: List<ChatAttachment>,
    currentSubject: String?,
    canRequestApproval: Boolean,
    isDeleting: Boolean,
    openingAttachmentId: Long?,
    deletingAttachmentId: Long?,
    onRequestApproval: () -> Unit,
    onOpenAttachment: (Long) -> Unit,
    onDeleteAttachment: (Long) -> Unit,
    onDeleteMessage: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message.senderDisplayName ?: message.senderSubject ?: "ISAC",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(message.body)
            if (attachments.isNotEmpty()) {
                attachments.forEach { attachment ->
                    AttachmentRow(
                        attachment = attachment,
                        canDelete = attachment.createdBySubject == currentSubject && !message.deleted,
                        isOpening = openingAttachmentId == attachment.id,
                        isDeleting = deletingAttachmentId == attachment.id,
                        onOpenAttachment = { onOpenAttachment(attachment.id) },
                        onDeleteAttachment = { onDeleteAttachment(attachment.id) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.createdAt ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (canRequestApproval) {
                        Button(onClick = onRequestApproval) {
                            Text("Vyziadat schvalenie")
                        }
                    }
                    if (message.deletable) {
                        IconButton(
                            onClick = onDeleteMessage,
                            enabled = !isDeleting
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Delete, contentDescription = "Zmazat spravu")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: ApprovalCase,
    currentSubject: String?,
    onDecision: (Long, ApprovalDecisionCode) -> Unit
) {
    val canDecide = approval.status == ApprovalStatus.PENDING &&
        (approval.competentSubject.isNullOrBlank() || approval.competentSubject == currentSubject)

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = approval.proposalCode ?: "Schvalenie dalsieho postupu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text("Stav: ${approval.status.name}")
            approval.proposalText?.let { Text(it) }
            Text(
                text = "Ziadal: ${approval.requestedBySubject ?: "-"} | Kompetentny: ${approval.competentSubject ?: "-"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canDecide) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDecision(approval.id, ApprovalDecisionCode.APPROVED) }) {
                        Text("Schvalit")
                    }
                    Button(onClick = { onDecision(approval.id, ApprovalDecisionCode.CHANGES_REQUIRED) }) {
                        Text("Na upravu")
                    }
                    Button(onClick = { onDecision(approval.id, ApprovalDecisionCode.REJECTED) }) {
                        Text("Zamietnut")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: ChatAttachment,
    canDelete: Boolean,
    isOpening: Boolean,
    isDeleting: Boolean,
    onOpenAttachment: () -> Unit,
    onDeleteAttachment: () -> Unit
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AttachmentPreviewImage(
                    filePath = attachment.localPreviewPath,
                    fileName = attachment.fileName,
                    onOpenAttachment = onOpenAttachment
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = attachment.fileName,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildAttachmentLine(attachment),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenAttachment,
                    enabled = !isOpening && !isDeleting
                ) {
                    if (isOpening) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Otvorit prilohu")
                    }
                }
                if (canDelete) {
                    IconButton(
                        onClick = onDeleteAttachment,
                        enabled = !isDeleting && !isOpening
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Delete, contentDescription = "Zmazat prilohu")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewImage(
    filePath: String?,
    fileName: String,
    onOpenAttachment: () -> Unit
) {
    val previewBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, filePath) {
        value = if (filePath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(filePath)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    if (previewBitmap != null) {
        Image(
            bitmap = previewBitmap!!,
            contentDescription = fileName,
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onOpenAttachment),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpenAttachment),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = fileName.substringAfterLast('.', "").uppercase().take(4).ifBlank { "FILE" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ComposerBar(
    text: String,
    pendingAttachments: List<LocalAttachmentDraft>,
    visibilityScope: VisibilityScope,
    isSending: Boolean,
    showVisibilityScope: Boolean,
    onTextChanged: (String) -> Unit,
    onPickAttachments: () -> Unit,
    onRemovePendingAttachment: (String) -> Unit,
    onVisibilityScopeChanged: (VisibilityScope) -> Unit,
    onSend: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (pendingAttachments.isEmpty()) {
                        "Nova sprava"
                    } else {
                        "${pendingAttachments.size} pripravenych priloh"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = onPickAttachments,
                    enabled = !isSending
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null)
                    Text("Priloha")
                }
            }
            if (pendingAttachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pendingAttachments.forEach { attachment ->
                        InputChip(
                            selected = true,
                            onClick = { onRemovePendingAttachment(attachment.uri) },
                            leadingIcon = {
                                Icon(Icons.Outlined.AttachFile, contentDescription = null)
                            },
                            trailingIcon = {
                                Icon(Icons.Outlined.Delete, contentDescription = null)
                            },
                            label = {
                                Text("${attachment.displayName} (${formatAttachmentSize(attachment.sizeBytes)})")
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (showVisibilityScope) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = visibilityScope.name,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        label = { Text("Viditelnost spravy") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        VisibilityScope.entries.forEach { scope ->
                            DropdownMenuItem(
                                text = { Text(scope.name) },
                                onClick = {
                                    onVisibilityScopeChanged(scope)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("Nova sprava") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSend,
                enabled = !isSending && (text.isNotBlank() || pendingAttachments.isNotEmpty()),
                modifier = Modifier.align(Alignment.End)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Odoslat")
                }
            }
        }
    }
}

private fun buildHeaderSubtitle(bundle: ConversationBundle?): String {
    if (bundle == null) {
        return "Nacitavam..."
    }
    return "${bundle.conversation.members.size} clenov | unread ${bundle.conversation.unreadCount}"
}

private fun buildAttachmentLine(attachment: ChatAttachment): String {
    val preview = if (attachment.previewAvailable) "preview" else "bez preview"
    val scan = attachment.scanStatus ?: "neznamy scan"
    return "${formatAttachmentSize(attachment.sizeBytes)}, $preview, $scan"
}

private fun conversationPanes(bundle: ConversationBundle?): List<ConversationPane> = buildList {
    add(ConversationPane.MESSAGES)
    if (bundle?.conversation?.approvalEnabled == true) {
        add(ConversationPane.ACTIONS)
    }
    if (bundle?.conversation?.type == ConversationType.GROUP_OPEN) {
        add(ConversationPane.GROUP)
    }
}

private fun ConversationPane.label(): String = when (this) {
    ConversationPane.MESSAGES -> "Spravy"
    ConversationPane.ACTIONS -> "Akcie"
    ConversationPane.GROUP -> "Skupina"
}

private fun formatAttachmentSize(sizeBytes: Long): String = when {
    sizeBytes >= 1024L * 1024L -> String.format("%.1f MB", sizeBytes / (1024f * 1024f))
    sizeBytes >= 1024L -> String.format("%.1f KB", sizeBytes / 1024f)
    else -> "$sizeBytes B"
}

private fun Uri.toLocalAttachmentDraft(context: android.content.Context): LocalAttachmentDraft? {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(this)
    val metadata = resolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                name to size
            } else {
                null
            }
        }
    val displayName = metadata?.first?.takeIf { it.isNotBlank() }
        ?: lastPathSegment
        ?: "attachment"
    return LocalAttachmentDraft(
        uri = toString(),
        displayName = displayName,
        sizeBytes = metadata?.second ?: 0L,
        mimeType = mimeType
    )
}

private fun android.content.ContentResolver.takePersistableReadPermissionSafely(uri: Uri) {
    runCatching {
        takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
