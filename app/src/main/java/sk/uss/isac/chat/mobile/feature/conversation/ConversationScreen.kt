package sk.uss.isac.chat.mobile.feature.conversation

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sk.uss.isac.chat.mobile.app.AppNavigationCoordinator
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
    conversationId: Long,
    appNavigationCoordinator: AppNavigationCoordinator,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAttachmentPickerDialog by remember { mutableStateOf(false) }

    fun addSelectedAttachments(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            viewModel.addPendingAttachments(uris.mapNotNull { uri ->
                context.contentResolver.takePersistableReadPermissionSafely(uri)
                uri.toLocalAttachmentDraft(context)
            })
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        addSelectedAttachments(uris)
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        addSelectedAttachments(uris)
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

    LaunchedEffect(uiState.infoMessage) {
        val message = uiState.infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeInfoMessage()
    }

    LaunchedEffect(viewModel) {
        viewModel.openExternalUrlEvents.collect { rawUrl ->
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    DisposableEffect(conversationId) {
        appNavigationCoordinator.markConversationVisible(conversationId)
        onDispose {
            appNavigationCoordinator.markConversationHidden(conversationId)
        }
    }

    ConversationScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onPaneSelected = viewModel::onPaneSelected,
        onComposerTextChanged = viewModel::onComposerTextChanged,
        onPickAttachments = { showAttachmentPickerDialog = true },
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
        onConsumeRequestedMessageTarget = viewModel::consumeRequestedMessageTarget,
        onConsumeRequestedAttachmentTarget = viewModel::consumeRequestedAttachmentTarget,
        onConsumeRequestedApprovalTarget = viewModel::consumeRequestedApprovalTarget,
        onDeleteAttachment = viewModel::deleteAttachment,
        onDeleteMessage = viewModel::deleteMessage,
        onDecision = viewModel::decideApproval,
        onOpenMessageUrl = viewModel::openMessageUrl
    )

    if (showAttachmentPickerDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentPickerDialog = false },
            title = { Text("Pridať prílohu") },
            text = { Text("Vyber, či chceš otvoriť fotky a videá, alebo všeobecný výber súborov.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAttachmentPickerDialog = false
                        mediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                ) {
                    Text("Fotky a videá")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showAttachmentPickerDialog = false
                            attachmentPicker.launch(arrayOf("*/*"))
                        }
                    ) {
                        Text("Súbory")
                    }
                    TextButton(onClick = { showAttachmentPickerDialog = false }) {
                        Text("Zrušiť")
                    }
                }
            }
        )
    }
}

@Composable
fun ConversationScreen(
    uiState: ConversationUiState,
    snackbarHostState: SnackbarHostState,
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
    onConsumeRequestedMessageTarget: () -> Unit,
    onConsumeRequestedAttachmentTarget: () -> Unit,
    onConsumeRequestedApprovalTarget: () -> Unit,
    onDeleteAttachment: (Long) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onDecision: (Long, ApprovalDecisionCode, String?) -> Unit,
    onOpenMessageUrl: (String) -> Unit
) {
    val bundle = uiState.bundle
    val panes = conversationPanes(bundle)
    var confirmDeleteMessageId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteAttachmentId by remember { mutableStateOf<Long?>(null) }
    var confirmRemoveMember by remember { mutableStateOf<ConversationMember?>(null) }
    var confirmLeaveConversation by remember { mutableStateOf(false) }
    var previewAttachment by remember { mutableStateOf<ChatAttachment?>(null) }
    var highlightedMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var highlightedAttachmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var highlightedApprovalCaseId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(uiState.requestedMessageId, bundle) {
        val requestedMessageId = uiState.requestedMessageId ?: return@LaunchedEffect
        val targetMessage = bundle?.messages?.firstOrNull { it.id == requestedMessageId } ?: return@LaunchedEffect
        highlightedMessageId = targetMessage.id
        onConsumeRequestedMessageTarget()
    }

    LaunchedEffect(uiState.requestedAttachmentId, bundle) {
        val requestedAttachmentId = uiState.requestedAttachmentId ?: return@LaunchedEffect
        val targetAttachment = bundle?.attachmentsByMessageId
            ?.values
            ?.flatten()
            ?.firstOrNull { it.id == requestedAttachmentId }
            ?: return@LaunchedEffect

        highlightedAttachmentId = targetAttachment.id
        if (targetAttachment.canPreviewInline()) {
            previewAttachment = targetAttachment
        } else {
            onOpenAttachment(targetAttachment.id)
        }
        onConsumeRequestedAttachmentTarget()
    }

    LaunchedEffect(uiState.requestedApprovalCaseId, bundle) {
        val requestedApprovalCaseId = uiState.requestedApprovalCaseId ?: return@LaunchedEffect
        val targetApproval = bundle?.approvals?.firstOrNull { it.id == requestedApprovalCaseId } ?: return@LaunchedEffect
        highlightedApprovalCaseId = targetApproval.id
        onConsumeRequestedApprovalTarget()
    }

    LaunchedEffect(highlightedMessageId, highlightedAttachmentId, highlightedApprovalCaseId) {
        val targetMessageId = highlightedMessageId
        val targetAttachmentId = highlightedAttachmentId
        val targetApprovalCaseId = highlightedApprovalCaseId
        if (targetMessageId == null && targetAttachmentId == null && targetApprovalCaseId == null) {
            return@LaunchedEffect
        }
        delay(4000)
        if (highlightedMessageId == targetMessageId) {
            highlightedMessageId = null
        }
        if (highlightedAttachmentId == targetAttachmentId) {
            highlightedAttachmentId = null
        }
        if (highlightedApprovalCaseId == targetApprovalCaseId) {
            highlightedApprovalCaseId = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Späť")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bundle?.conversation?.title ?: "Konverzácia",
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
                        Icon(Icons.Outlined.Refresh, contentDescription = "Obnoviť")
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
                    isRealtimeConnected = uiState.isRealtimeConnected,
                    hasQueuedMessageRetry = uiState.hasQueuedMessageRetry,
                    queuedRetryKind = uiState.queuedRetryKind,
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
                    Text(uiState.error ?: "Konverzácia nie je dostupná.")
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
                    highlightedApprovalCaseId = highlightedApprovalCaseId,
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
                    onRemoveMember = { memberId ->
                        confirmRemoveMember = bundle.conversation.members.firstOrNull { it.id == memberId }
                    },
                    onLeaveConversation = { confirmLeaveConversation = true }
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
                    highlightedMessageId = highlightedMessageId,
                    highlightedAttachmentId = highlightedAttachmentId,
                    onApprovalMessageSelected = onApprovalMessageSelected,
                    onPreviewAttachment = { previewAttachment = it },
                    onOpenAttachment = onOpenAttachment,
                    onDeleteAttachment = { attachmentId -> confirmDeleteAttachmentId = attachmentId },
                    onDeleteMessage = { messageId -> confirmDeleteMessageId = messageId },
                    onOpenMessageUrl = onOpenMessageUrl
                )
            }
        }
    }

    confirmDeleteMessageId?.let { messageId ->
        ConfirmActionDialog(
            title = "Zmazať správu?",
            text = "Správa bude odstránená aj s naviazanými prílohami.",
            confirmLabel = "Zmazať",
            onDismiss = { confirmDeleteMessageId = null },
            onConfirm = {
                confirmDeleteMessageId = null
                onDeleteMessage(messageId)
            }
        )
    }

    confirmDeleteAttachmentId?.let { attachmentId ->
        ConfirmActionDialog(
            title = "Zmazať prílohu?",
            text = "Príloha sa odstráni z tejto správy pre všetkých členov konverzácie.",
            confirmLabel = "Zmazať",
            onDismiss = { confirmDeleteAttachmentId = null },
            onConfirm = {
                confirmDeleteAttachmentId = null
                onDeleteAttachment(attachmentId)
            }
        )
    }

    confirmRemoveMember?.let { member ->
        ConfirmActionDialog(
            title = "Odobrať člena?",
            text = "Používateľ ${member.displayName} bude odobraný zo skupiny.",
            confirmLabel = "Odobrať",
            onDismiss = { confirmRemoveMember = null },
            onConfirm = {
                confirmRemoveMember = null
                onRemoveMember(member.id)
            }
        )
    }

    if (confirmLeaveConversation) {
        ConfirmActionDialog(
            title = "Opustiť skupinu?",
            text = "Po potvrdení opustíš konverzáciu. Ak si vlastník, backend pridelí nového vlastníka.",
            confirmLabel = "Opustiť",
            onDismiss = { confirmLeaveConversation = false },
            onConfirm = {
                confirmLeaveConversation = false
                onLeaveConversation()
            }
        )
    }

    previewAttachment?.let { attachment ->
        AttachmentPreviewDialog(
            attachment = attachment,
            onDismiss = {
                previewAttachment = null
                highlightedAttachmentId = null
            },
            onOpenOriginal = {
                previewAttachment = null
                highlightedAttachmentId = null
                onOpenAttachment(attachment.id)
            }
        )
    }
}

@Composable
private fun MessagePane(
    modifier: Modifier,
    uiState: ConversationUiState,
    bundle: ConversationBundle,
    highlightedMessageId: Long?,
    highlightedAttachmentId: Long?,
    onApprovalMessageSelected: (Long) -> Unit,
    onPreviewAttachment: (ChatAttachment) -> Unit,
    onOpenAttachment: (Long) -> Unit,
    onDeleteAttachment: (Long) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onOpenMessageUrl: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.scrollToBottomSignal, bundle.messages.size, uiState.error) {
        if (uiState.scrollToBottomSignal <= 0L || highlightedMessageId != null || bundle.messages.isEmpty()) {
            return@LaunchedEffect
        }
        val headerItems = 1 + if (uiState.error != null) 1 else 0
        val targetIndex = headerItems + bundle.messages.lastIndex
        // Large conversations can render after this effect starts, so wait until the target row exists.
        repeat(8) {
            if (listState.layoutInfo.totalItemsCount > targetIndex) {
                listState.scrollToItem(targetIndex)
                return@LaunchedEffect
            }
            delay(50)
        }
        if (listState.layoutInfo.totalItemsCount > targetIndex) {
            listState.scrollToItem(targetIndex)
        }
    }

    LaunchedEffect(highlightedMessageId, uiState.error, bundle.messages) {
        val targetMessageId = highlightedMessageId ?: return@LaunchedEffect
        val targetIndex = bundle.messages.indexOfFirst { it.id == targetMessageId }
        if (targetIndex >= 0) {
            val headerItems = 1 + if (uiState.error != null) 1 else 0
            listState.animateScrollToItem(headerItems + targetIndex)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
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
                isHighlighted = highlightedMessageId == message.id,
                highlightedAttachmentId = highlightedAttachmentId,
                canRequestApproval = canRequestApproval,
                isDeleting = uiState.deletingMessageId == message.id,
                openingAttachmentId = uiState.openingAttachmentId,
                deletingAttachmentId = uiState.deletingAttachmentId,
                onRequestApproval = { onApprovalMessageSelected(message.id) },
                onPreviewAttachment = onPreviewAttachment,
                onOpenAttachment = onOpenAttachment,
                onDeleteAttachment = onDeleteAttachment,
                onDeleteMessage = { onDeleteMessage(message.id) },
                onOpenMessageUrl = onOpenMessageUrl
            )
        }
    }
}

@Composable
private fun ApprovalPane(
    modifier: Modifier,
    uiState: ConversationUiState,
    bundle: ConversationBundle,
    highlightedApprovalCaseId: Long?,
    onApprovalMessageSelected: (Long) -> Unit,
    onApprovalCompetentChanged: (String) -> Unit,
    onApprovalProposalCodeChanged: (String) -> Unit,
    onApprovalProposalTextChanged: (String) -> Unit,
    onSubmitApproval: () -> Unit,
    onClearApprovalDraft: () -> Unit,
    onDecision: (Long, ApprovalDecisionCode, String?) -> Unit
) {
    val listState = rememberLazyListState()
    val selectableMessages = bundle.messages.filter { message ->
        message.senderSubject == uiState.currentSubject &&
            !message.deleted &&
            message.messageType == MessageType.USER_MESSAGE
    }
    val selectedMessage = selectableMessages.firstOrNull { it.id == uiState.selectedApprovalMessageId }

    LaunchedEffect(highlightedApprovalCaseId, bundle.approvals, uiState.error) {
        val targetApprovalCaseId = highlightedApprovalCaseId ?: return@LaunchedEffect
        val targetIndex = bundle.approvals.indexOfFirst { it.id == targetApprovalCaseId }
        if (targetIndex >= 0) {
            val headerItems = 1 + if (uiState.error != null) 1 else 0
            listState.animateScrollToItem(headerItems + targetIndex)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
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
                        Text("Zatiaľ bez schvaľovacích prípadov", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Po odoslaní žiadosti sa tu zobrazí história schvaľovania rovnako ako vo widgete.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(bundle.approvals, key = { it.id }) { approval ->
                ApprovalCard(
                    approval = approval,
                    directoryUsers = uiState.directoryUsers,
                    currentSubject = uiState.currentSubject,
                    isHighlighted = highlightedApprovalCaseId == approval.id,
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
                        Text("Správa skupiny", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                    "V tejto konverzácii zatiaľ nemáš vlastnú správu, nad ktorou sa dá založiť schválenie.",
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
                    label = { Text("Cieľová správa") },
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
                    label = { Text("Kompetentný") },
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
                label = { Text("Kód postupu") },
                singleLine = true
            )

            OutlinedTextField(
                value = proposalText,
                onValueChange = onApprovalProposalTextChanged,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("Návrh ďalšieho postupu") }
            )

            if (selectedMessage != null) {
                Text(
                    text = "Vybraná správa: ${selectedMessage.body.take(180)}",
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
                        Text("Založiť schválenie")
                    }
                }
                OutlinedButton(
                    onClick = onClearApprovalDraft,
                    enabled = !isSubmitting
                ) {
                    Text("Zrušiť výber")
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
                label = { Text("Názov skupiny") },
                singleLine = true
            )
            Button(onClick = onRenameGroup, enabled = !isSaving && title.isNotBlank()) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Uložiť názov")
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
                Text("Pridať člena", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (availableUsers.isEmpty()) {
                Text(
                    "Všetci dostupní používatelia už sú v skupine.",
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
                        Text("Pridať do skupiny")
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
            Text("Členovia skupiny", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                                Text(if (targetRole == MemberRole.ADMIN) "Spraviť adminom" else "Spraviť členom")
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
                                Text("Odobrať")
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
                Text("Opustiť skupinu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Ak si vlastnik, backend automaticky priradi noveho vlastnika podla pravidiel skupiny.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onLeaveConversation, enabled = !isLeaving) {
                if (isLeaving) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Opustiť konverzáciu")
                }
            }
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zrušiť")
            }
        }
    )
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
    isHighlighted: Boolean,
    highlightedAttachmentId: Long?,
    canRequestApproval: Boolean,
    isDeleting: Boolean,
    openingAttachmentId: Long?,
    deletingAttachmentId: Long?,
    onRequestApproval: () -> Unit,
    onPreviewAttachment: (ChatAttachment) -> Unit,
    onOpenAttachment: (Long) -> Unit,
    onDeleteAttachment: (Long) -> Unit,
    onDeleteMessage: () -> Unit,
    onOpenMessageUrl: (String) -> Unit
) {
    Card(
        colors = if (isHighlighted) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        border = if (isHighlighted) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
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
            LinkifiedMessageText(
                text = message.body,
                onOpenUrl = onOpenMessageUrl
            )
            if (attachments.isNotEmpty()) {
                attachments.forEach { attachment ->
                    AttachmentRow(
                        attachment = attachment,
                        isHighlighted = highlightedAttachmentId == attachment.id,
                        canDelete = attachment.createdBySubject == currentSubject && !message.deleted,
                        isOpening = openingAttachmentId == attachment.id,
                        isDeleting = deletingAttachmentId == attachment.id,
                        onPreviewAttachment = { onPreviewAttachment(attachment) },
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
                            Text("Vyžiadať schválenie")
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
                                Icon(Icons.Outlined.Delete, contentDescription = "Zmazať správu")
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
    directoryUsers: List<DirectoryUser>,
    currentSubject: String?,
    isHighlighted: Boolean,
    onDecision: (Long, ApprovalDecisionCode, String?) -> Unit
) {
    val canDecide = approval.status == ApprovalStatus.PENDING &&
        (approval.competentSubject.isNullOrBlank() || approval.competentSubject == currentSubject)
    var decisionNote by rememberSaveable(approval.id) { mutableStateOf("") }

    Card(
        border = if (isHighlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = approval.proposalCode ?: "Schválenie ďalšieho postupu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text("Stav: ${approval.status.readableLabel()}")
            approval.proposalText?.let { Text(it) }
            Text(
                text = "Žiadal: ${readableApprovalActor(approval.requestedBySubject, directoryUsers)} | " +
                    "Kompetentný: ${readableApprovalActor(approval.competentSubject, directoryUsers)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (approval.decisionCode != null || !approval.decisionNote.isNullOrBlank()) {
                Text(
                    text = buildString {
                        append("Rozhodnutie: ")
                        append(approval.decisionCode?.readableLabel() ?: "bez kódu")
                        if (!approval.decisionNote.isNullOrBlank()) {
                            append(" | ")
                            append(approval.decisionNote)
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canDecide) {
                OutlinedTextField(
                    value = decisionNote,
                    onValueChange = { decisionNote = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("Poznámka k rozhodnutiu") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDecision(approval.id, ApprovalDecisionCode.APPROVED, decisionNote) }) {
                        Text("Schváliť")
                    }
                    Button(onClick = { onDecision(approval.id, ApprovalDecisionCode.CHANGES_REQUIRED, decisionNote) }) {
                        Text("Na úpravu")
                    }
                    Button(onClick = { onDecision(approval.id, ApprovalDecisionCode.REJECTED, decisionNote) }) {
                        Text("Zamietnuť")
                    }
                }
            }
        }
    }
}

private fun readableApprovalActor(subject: String?, directoryUsers: List<DirectoryUser>): String {
    if (subject.isNullOrBlank()) {
        return "-"
    }
    return directoryUsers.firstOrNull { it.subject == subject }?.displayName?.takeIf { it.isNotBlank() } ?: subject
}

private fun ApprovalStatus.readableLabel(): String = when (this) {
    ApprovalStatus.PENDING -> "Čaká na rozhodnutie"
    ApprovalStatus.APPROVED -> "Schválené"
    ApprovalStatus.CHANGES_REQUIRED -> "Vrátené na úpravu"
    ApprovalStatus.REJECTED -> "Zamietnuté"
    ApprovalStatus.UNKNOWN -> "Neznámy stav"
}

private fun ApprovalDecisionCode.readableLabel(): String = when (this) {
    ApprovalDecisionCode.APPROVED -> "Schválené"
    ApprovalDecisionCode.CHANGES_REQUIRED -> "Vrátené na úpravu"
    ApprovalDecisionCode.REJECTED -> "Zamietnuté"
}

@Composable
private fun AttachmentRow(
    attachment: ChatAttachment,
    isHighlighted: Boolean,
    canDelete: Boolean,
    isOpening: Boolean,
    isDeleting: Boolean,
    onPreviewAttachment: () -> Unit,
    onOpenAttachment: () -> Unit,
    onDeleteAttachment: () -> Unit
) {
    Card(
        colors = if (isHighlighted) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        },
        border = if (isHighlighted) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
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
                    onPreviewAttachment = if (attachment.canPreviewInline()) onPreviewAttachment else onOpenAttachment
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
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Otvoriť prílohu")
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
                            Icon(Icons.Outlined.Delete, contentDescription = "Zmazať prílohu")
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
    onPreviewAttachment: () -> Unit
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
                .clickable(onClick = onPreviewAttachment),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onPreviewAttachment),
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

@Composable
private fun LinkifiedMessageText(
    text: String,
    onOpenUrl: (String) -> Unit
) {
    val bodyColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                highlightColor = android.graphics.Color.TRANSPARENT
                setLineSpacing(0f, 1.14f)
            }
        },
        update = { textView ->
            val spannable = SpannableString(text)
            Linkify.addLinks(spannable, Linkify.WEB_URLS)
            spannable.getSpans(0, spannable.length, URLSpan::class.java).forEach { span ->
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                val flags = spannable.getSpanFlags(span)
                spannable.removeSpan(span)
                spannable.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            onOpenUrl(span.url)
                        }
                    },
                    start,
                    end,
                    flags or Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            textView.text = spannable
            textView.setTextColor(bodyColor)
            textView.setLinkTextColor(linkColor)
            textView.textSize = 15f
            textView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AttachmentPreviewDialog(
    attachment: ChatAttachment,
    onDismiss: () -> Unit,
    onOpenOriginal: () -> Unit
) {
    val previewBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, attachment.localPreviewPath) {
        value = attachment.localPreviewPath?.let { previewPath ->
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(previewPath)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.86f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = attachment.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!,
                            contentDescription = attachment.fileName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                                .clip(MaterialTheme.shapes.large),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = "Náhľad nie je k dispozícii. Súbor môžeš otvoriť pôvodným tlačidlom.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = buildAttachmentLine(attachment),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Zavrieť")
                        }
                        if (previewBitmap != null) {
                            TextButton(onClick = onOpenOriginal) {
                                Text("Otvoriť originál")
                            }
                        }
                    }
                }
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
    isRealtimeConnected: Boolean?,
    hasQueuedMessageRetry: Boolean,
    queuedRetryKind: QueuedRetryKind?,
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
            if (hasQueuedMessageRetry) {
                Text(
                    text = when (queuedRetryKind) {
                        QueuedRetryKind.ATTACHMENTS -> "Prílohy čakajú na obnovenie spojenia. Nahrajú sa znovu automaticky."
                        else -> "Správa čaká na obnovenie spojenia. Odošle sa znovu automaticky."
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if (isRealtimeConnected == false) {
                Text(
                    text = "Spojenie je momentalne nestabilne. Nove spravy sa mozu obnovit s oneskorenim.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (pendingAttachments.isEmpty()) {
                        "Nová správa"
                    } else {
                        "${pendingAttachments.size} pripravených príloh"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = onPickAttachments,
                    enabled = !isSending
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null)
                    Text("Príloha")
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
                        label = { Text("Viditeľnosť správy") },
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
                label = { Text("Nová správa") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                trailingIcon = {
                    IconButton(
                        onClick = onSend,
                        enabled = !isSending && (text.isNotBlank() || pendingAttachments.isNotEmpty())
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Odoslať správu"
                            )
                        }
                    }
                }
            )
        }
    }
}

private fun buildHeaderSubtitle(bundle: ConversationBundle?): String {
    if (bundle == null) {
        return "Načítavam..."
    }
    return "${bundle.conversation.members.size} členov | neprečítané ${bundle.conversation.unreadCount}"
}

private fun buildAttachmentLine(attachment: ChatAttachment): String {
    val preview = if (attachment.previewAvailable) "náhľad" else "bez náhľadu"
    val contentType = attachment.contentType?.substringBefore(';')?.takeIf { it.isNotBlank() } ?: "neznámy typ"
    val scan = attachment.scanStatus ?: "neznámy scan"
    return "${formatAttachmentSize(attachment.sizeBytes)}, $contentType, $preview, $scan"
}

private fun ChatAttachment.canPreviewInline(): Boolean {
    if (!previewAvailable || localPreviewPath.isNullOrBlank()) {
        return false
    }
    val type = contentType?.lowercase().orEmpty()
    if (type.startsWith("image/")) {
        return true
    }
    val fileExtension = fileName.substringAfterLast('.', "").lowercase()
    return fileExtension in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
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
    ConversationPane.MESSAGES -> "Správy"
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
