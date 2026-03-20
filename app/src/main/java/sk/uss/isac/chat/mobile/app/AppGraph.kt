package sk.uss.isac.chat.mobile.app

import android.content.Context
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.core.data.remote.ChatApi
import sk.uss.isac.chat.mobile.core.data.repository.ChatRepository
import sk.uss.isac.chat.mobile.core.data.repository.NetworkChatRepository
import sk.uss.isac.chat.mobile.core.network.ApiHeadersInterceptor
import sk.uss.isac.chat.mobile.core.network.AuthInterceptor
import sk.uss.isac.chat.mobile.core.network.ChatRealtimeClient
import sk.uss.isac.chat.mobile.core.network.StompChatRealtimeClient
import sk.uss.isac.chat.mobile.core.session.SessionStore
import java.util.concurrent.TimeUnit

class AppGraph(context: Context) {
    val sessionStore = SessionStore(context)

    private val gson = Gson()

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
        okHttpClient = okHttpClient,
        gson = gson
    )

    val chatRepository: ChatRepository = NetworkChatRepository(
        chatApi = chatApi,
        appContext = context.applicationContext,
        sessionStore = sessionStore,
        realtimeClient = realtimeClient,
        okHttpClient = okHttpClient
    )
}
