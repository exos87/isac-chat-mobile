package sk.uss.isac.chat.mobile.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sk.uss.isac.chat.mobile.core.session.UserSession

internal fun isSessionDestination(url: HttpUrl, session: UserSession?): Boolean =
    session != null && listOf(session.baseUrl, session.profileApiUrl, session.wsUrl)
        .any { configured ->
            val base = configured.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://").toHttpUrlOrNull()
            base != null && url.scheme == base.scheme && url.host == base.host && url.port == base.port &&
                (url.encodedPath == base.encodedPath.trimEnd('/') || url.encodedPath.startsWith(base.encodedPath.trimEnd('/') + "/"))
        }
