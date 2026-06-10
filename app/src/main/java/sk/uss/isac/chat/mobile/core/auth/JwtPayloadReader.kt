package sk.uss.isac.chat.mobile.core.auth

import java.util.Base64

internal object JwtPayloadReader {

    fun readStringClaim(token: String, claimName: String): String? {
        val payload = decodePayload(token) ?: return null
        return Regex(""""${Regex.escape(claimName)}"\s*:\s*"([^"]+)"""").find(payload)
            ?.groupValues
            ?.getOrNull(1)
    }

    fun readExpMillis(token: String): Long? {
        val payload = decodePayload(token) ?: return null
        val expSeconds = Regex(""""exp"\s*:\s*(\d+)""").find(payload)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: return null
        return expSeconds * 1000L
    }

    private fun decodePayload(token: String): String? {
        val parts = token.split(".")
        if (parts.size < 2) {
            return null
        }
        return runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        }.getOrNull()
    }
}
