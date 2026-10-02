package sk.uss.isac.chat.mobile.core.network

import sk.uss.isac.chat.mobile.core.session.localOwnerKey
import okhttp3.Interceptor
import okhttp3.Response
import sk.uss.isac.chat.mobile.core.session.SessionStore

class AuthInterceptor(
    private val sessionProvider: () -> sk.uss.isac.chat.mobile.core.session.UserSession?
) : Interceptor {
    constructor(sessionStore: SessionStore) : this({ sessionStore.currentSession() })
    override fun intercept(chain: Interceptor.Chain): Response {
        val current = sessionProvider()
        val captured = chain.request().tag(OutgoingSessionFence::class.java)?.session
        if (captured != null && (captured.sessionEpoch != current?.sessionEpoch ||
            (captured != current && (captured.localOwnerKey() == null || captured.localOwnerKey() != current?.localOwnerKey())))) {
            throw java.io.IOException("Outgoing session owner changed")
        }
        val session = captured ?: current
        val token = session?.accessToken
        val requestBuilder = chain.request().newBuilder()
        if (chain.request().header("Authorization") == null && !token.isNullOrBlank() &&
            isSessionDestination(chain.request().url, session)) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(requestBuilder.build())
    }
}
