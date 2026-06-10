package sk.uss.isac.chat.mobile.core.auth

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

internal class SessionCookieJar : CookieJar {
    private val cookiesByHost = linkedMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) {
            return
        }
        val existingCookies = cookiesByHost.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { cookie ->
            existingCookies.removeAll { current ->
                current.name == cookie.name &&
                    current.domain == cookie.domain &&
                    current.path == cookie.path
            }
            existingCookies += cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val storedCookies = cookiesByHost[url.host].orEmpty()
        val now = System.currentTimeMillis()
        return storedCookies
            .filter { cookie -> cookie.expiresAt >= now && cookie.matches(url) }
    }
}
