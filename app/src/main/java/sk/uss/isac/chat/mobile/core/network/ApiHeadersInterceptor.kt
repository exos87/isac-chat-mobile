package sk.uss.isac.chat.mobile.core.network

import okhttp3.Interceptor
import okhttp3.Response
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.core.session.SessionStore

class ApiHeadersInterceptor(
    private val sessionProvider: () -> sk.uss.isac.chat.mobile.core.session.UserSession?
) : Interceptor {
    constructor(sessionStore: SessionStore) : this({ sessionStore.currentSession() })
    override fun intercept(chain: Interceptor.Chain): Response {
        val currentSession = chain.request().tag(OutgoingSessionFence::class.java)?.session ?: sessionProvider()
        val builder = chain.request().newBuilder()
        if (chain.request().header("X-Api-Type") == null &&
            isSessionDestination(chain.request().url, currentSession)) {
            builder.header("X-Api-Type", currentSession?.xApiType ?: BuildConfig.X_API_TYPE)
        }
        val request = builder.build()
        return chain.proceed(request)
    }
}
