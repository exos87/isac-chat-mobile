package sk.uss.isac.chat.mobile.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import sk.uss.isac.chat.mobile.feature.conversation.ConversationPane
import sk.uss.isac.chat.mobile.feature.conversation.ConversationRoute
import sk.uss.isac.chat.mobile.feature.conversation.ConversationViewModel
import sk.uss.isac.chat.mobile.feature.home.HomeOpenConversationRequest
import sk.uss.isac.chat.mobile.feature.home.HomeRoute
import sk.uss.isac.chat.mobile.feature.home.HomeViewModel
import sk.uss.isac.chat.mobile.feature.session.SessionRoute
import sk.uss.isac.chat.mobile.feature.session.SessionViewModel

@Composable
fun IsacChatMobileApp(appGraph: AppGraph) {
    val session by appGraph.sessionStore.session.collectAsStateWithLifecycle()

    if (session == null) {
        val sessionViewModel: SessionViewModel = viewModel(
            factory = SessionViewModel.factory(
                repository = appGraph.chatRepository,
                oidcSsoCoordinator = appGraph.oidcSsoCoordinator,
                pushRegistrationStore = appGraph.pushRegistrationStore,
                pushTokenSyncCoordinator = appGraph.pushTokenSyncCoordinator,
                pushMessagingClient = appGraph.pushMessagingClient
            )
        )
        SessionRoute(viewModel = sessionViewModel)
        return
    }

    val navController = rememberNavController()
    val pendingChatDestination by appGraph.appNavigationCoordinator.pendingChatDestination.collectAsStateWithLifecycle()

    LaunchedEffect(pendingChatDestination) {
        val destination = pendingChatDestination ?: return@LaunchedEffect
        val route = buildConversationRoute(
            conversationId = destination.conversationId,
            messageId = destination.messageId,
            attachmentId = destination.attachmentId,
            approvalCaseId = destination.approvalCaseId,
            initialPane = destination.initialPane
        )
        navController.navigate(route) {
            launchSingleTop = true
        }
        appGraph.appNavigationCoordinator.consumePendingConversation(destination)
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(
                    repository = appGraph.chatRepository,
                    appForegroundEvents = appGraph.appForegroundCoordinator,
                    appPushEvents = appGraph.appPushEventCoordinator,
                    networkConnectivityObserver = appGraph.networkConnectivityObserver
                )
            )
            HomeRoute(
                viewModel = homeViewModel,
                onOpenConversationRequest = { request ->
                    navController.navigate(buildConversationRoute(request))
                }
            )
        }
        composable(
            route = "conversation/{conversationId}?messageId={messageId}&attachmentId={attachmentId}&approvalCaseId={approvalCaseId}&pane={pane}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.LongType },
                navArgument("messageId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("attachmentId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("approvalCaseId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("pane") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: return@composable
            val messageId = backStackEntry.arguments?.getLong("messageId")?.takeIf { it > 0 }
            val attachmentId = backStackEntry.arguments?.getLong("attachmentId")?.takeIf { it > 0 }
            val approvalCaseId = backStackEntry.arguments?.getLong("approvalCaseId")?.takeIf { it > 0 }
            val initialPane = backStackEntry.arguments?.getString("pane").toConversationPaneOrNull()
            val conversationViewModel: ConversationViewModel = viewModel(
                factory = ConversationViewModel.factory(
                    conversationId = conversationId,
                    repository = appGraph.chatRepository,
                    appForegroundEvents = appGraph.appForegroundCoordinator,
                    appPushEvents = appGraph.appPushEventCoordinator,
                    networkConnectivityObserver = appGraph.networkConnectivityObserver,
                    conversationDraftStore = appGraph.conversationDraftStore,
                    conversationRetryStore = appGraph.conversationRetryStore,
                    initialMessageId = messageId,
                    initialAttachmentId = attachmentId,
                    initialApprovalCaseId = approvalCaseId,
                    initialPane = initialPane
                )
            )
            ConversationRoute(
                viewModel = conversationViewModel,
                conversationId = conversationId,
                appNavigationCoordinator = appGraph.appNavigationCoordinator,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun String?.toConversationPaneOrNull(): ConversationPane? {
    return when (this?.trim()?.lowercase()) {
        "messages" -> ConversationPane.MESSAGES
        "actions" -> ConversationPane.ACTIONS
        "group" -> ConversationPane.GROUP
        else -> null
    }
}

internal fun buildConversationRoute(request: HomeOpenConversationRequest): String {
    return buildConversationRoute(
        conversationId = request.conversationId,
        approvalCaseId = request.approvalCaseId,
        initialPane = request.initialPane
    )
}

internal fun buildConversationRoute(
    conversationId: Long,
    messageId: Long? = null,
    attachmentId: Long? = null,
    approvalCaseId: Long? = null,
    initialPane: String? = null
): String {
    return buildString {
        append("conversation/")
        append(conversationId)
        val queryParts = buildList {
            messageId?.let {
                add("messageId=$it")
            }
            attachmentId?.let {
                add("attachmentId=$it")
            }
            approvalCaseId?.let {
                add("approvalCaseId=$it")
            }
            initialPane?.trim()?.takeIf { it.isNotBlank() }?.let { pane ->
                add("pane=$pane")
            }
        }
        if (queryParts.isNotEmpty()) {
            append("?")
            append(queryParts.joinToString("&"))
        }
    }
}
