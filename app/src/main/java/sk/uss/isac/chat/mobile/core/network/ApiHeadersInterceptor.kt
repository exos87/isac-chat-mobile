package sk.uss.isac.chat.mobile.core.network

import okhttp3.Interceptor
import okhttp3.Response
import sk.uss.isac.chat.mobile.BuildConfig
import sk.uss.isac.chat.mobile.core.session.SessionStore

class ApiHeadersInterceptor(
    private val sessionStore: SessionStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val currentSession = sessionStore.currentSession()
        val request = chain.request().newBuilder()
            .header("X-Api-Type", currentSession?.xApiType ?: BuildConfig.X_API_TYPE)
            .header("Accept", "application/json")
            .build()
        return chain.proceed(request)
    }
}

