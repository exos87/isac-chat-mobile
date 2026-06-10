package sk.uss.isac.chat.mobile.app

import android.content.Context
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.core.auth.OidcSsoCoordinator
import sk.uss.isac.chat.mobile.core.conversation.SharedPreferencesConversationDraftStore
import sk.uss.isac.chat.mobile.core.conversation.SharedPreferencesConversationRetryStore
import sk.uss.isac.chat.mobile.core.data.remote.ChatApi
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository
import sk.uss.isac.chat.mobile.core.data.repository.NetworkChatRepository
import sk.uss.isac.chat.mobile.core.network.ApiHeadersInterceptor
import sk.uss.isac.chat.mobile.core.network.AuthInterceptor
import sk.uss.isac.chat.mobile.core.network.ChatRealtimeClient
import sk.uss.isac.chat.mobile.core.network.OkHttpStompSocketFactory
import sk.uss.isac.chat.mobile.core.network.StompChatRealtimeClient
import sk.uss.isac.chat.mobile.core.notifications.ChatNotificationCoordinator
import sk.uss.isac.chat.mobile.core.notifications.ChatPushPayloadParser
import sk.uss.isac.chat.mobile.core.notifications.FirebasePushMessagingClient
import sk.uss.isac.chat.mobile.core.notifications.PushMessagingClient
import sk.uss.isac.chat.mobile.core.notifications.PushTokenSyncCoordinator
import sk.uss.isac.chat.mobile.core.notifications.SharedPreferencesPushRegistrationStore
import sk.uss.isac.chat.mobile.core.session.SessionStore
import java.util.concurrent.TimeUnit

class AppGraph(context: Context) {
    val appNavigationCoordinator = AppNavigationCoordinator()
    val appForegroundCoordinator = AppForegroundCoordinator()
    val appPushEventCoordinator = AppPushEventCoordinator()
    val networkConnectivityObserver = AndroidNetworkConnectivityObserver(context.applicationContext)
    val sessionStore = SessionStore(context)
    val pushRegistrationStore = SharedPreferencesPushRegistrationStore(context.applicationContext)
    private val gson = Gson()
    val conversationDraftStore = SharedPreferencesConversationDraftStore(context.applicationContext, gson)
    val conversationRetryStore = SharedPreferencesConversationRetryStore(context.applicationContext, gson)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(ApiHeadersInterceptor(sessionStore))
        .addInterceptor(AuthInterceptor(sessionStore))
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        )
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://localhost/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val chatApi: ChatApi = retrofit.create(ChatApi::class.java)

    val realtimeClient: ChatRealtimeClient = StompChatRealtimeClient(
        socketFactory = OkHttpStompSocketFactory(okHttpClient),
        gson = gson
    )

    val chatRepository: ChatRepository = NetworkChatRepository(
        chatApi = chatApi,
        appContext = context.applicationContext,
        sessionStore = sessionStore,
        realtimeClient = realtimeClient,
        okHttpClient = okHttpClient,
        gson = gson
    )

    val oidcSsoCoordinator = OidcSsoCoordinator(
        sessionStore = sessionStore,
        repository = chatRepository,
        okHttpClient = okHttpClient
    )

    val notificationCoordinator = ChatNotificationCoordinator(
        appContext = context.applicationContext,
        repository = chatRepository,
        appNavigationCoordinator = appNavigationCoordinator
    )

    val pushPayloadParser = ChatPushPayloadParser()
    val pushMessagingClient: PushMessagingClient = FirebasePushMessagingClient(context.applicationContext)

    val pushTokenSyncCoordinator = PushTokenSyncCoordinator(
        repository = chatRepository,
        registrationStore = pushRegistrationStore,
        pushMessagingClient = pushMessagingClient
    )
}
