package sk.uss.isac.chat.mobile.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import sk.uss.isac.chat.mobile.feature.conversation.ConversationRoute
import sk.uss.isac.chat.mobile.feature.conversation.ConversationViewModel
import sk.uss.isac.chat.mobile.feature.home.HomeRoute
import sk.uss.isac.chat.mobile.feature.home.HomeViewModel
import sk.uss.isac.chat.mobile.feature.session.SessionRoute
import sk.uss.isac.chat.mobile.feature.session.SessionViewModel

@Composable
fun IsacChatMobileApp(appGraph: AppGraph) {
    val session by appGraph.sessionStore.session.collectAsStateWithLifecycle()

    if (session == null) {
        val sessionViewModel: SessionViewModel = viewModel(
            factory = SessionViewModel.factory(appGraph.chatRepository)
        )
        SessionRoute(viewModel = sessionViewModel)
        return
    }

    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(appGraph.chatRepository)
            )
            HomeRoute(
                viewModel = homeViewModel,
                onConversationSelected = { conversationId ->
                    navController.navigate("conversation/$conversationId")
                }
            )
        }
        composable(
            route = "conversation/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: return@composable
            val conversationViewModel: ConversationViewModel = viewModel(
                factory = ConversationViewModel.factory(conversationId, appGraph.chatRepository)
            )
            ConversationRoute(
                viewModel = conversationViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
