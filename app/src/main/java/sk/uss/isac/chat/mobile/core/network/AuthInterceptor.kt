package sk.uss.isac.chat.mobile.core.network

import okhttp3.Interceptor
import okhttp3.Response
import sk.uss.isac.chat.mobile.core.session.SessionStore

class AuthInterceptor(
    private val sessionStore: SessionStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionStore.currentSession()?.accessToken
        val requestBuilder = chain.request().newBuilder()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(requestBuilder.build())
    }
}
