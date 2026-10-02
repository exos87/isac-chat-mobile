package sk.uss.isac.chat.mobile.core.session

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sk.uss.isac.chat.mobile.core.auth.JwtPayloadReader
import java.security.MessageDigest

/** Local isolation key only; JWT claims here do not grant server authorization. */
fun UserSession.localOwnerKey(): String? {
    val url = baseUrl.toHttpUrlOrNull() ?: return null
    val subject = JwtPayloadReader.readStringClaim(accessToken, "sub") ?: return null
    val issuer = JwtPayloadReader.readStringClaim(accessToken, "iss") ?: return null
    val tenants = listOf("tenant_id", "tenantId", "tenant")
        .mapNotNull { JwtPayloadReader.readStringClaim(accessToken, it)?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
    if (tenants.size != 1) return null
    val tenant = tenants.single()
    return ownerDigest(listOf(url.toString().trimEnd('/'), issuer, tenant, subject).joinToString("\u0000"))
}

internal fun ownerDigest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
