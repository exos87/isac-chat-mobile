package sk.uss.isac.chat.mobile.core.auth

import retrofit2.HttpException

internal const val SESSION_REAUTH_REQUIRED_MESSAGE =
    "Prihl\u00e1senie u\u017e nie je platn\u00e9. Prihl\u00e1s sa pros\u00edm znova."

internal fun Throwable.isUnauthorizedApiFailure(): Boolean {
    if (this is HttpException) {
        return code() == 401
    }
    val normalizedMessage = message?.lowercase().orEmpty()
    return normalizedMessage.contains("(401)") ||
        normalizedMessage == "unauthorized" ||
        (normalizedMessage.contains("401") && normalizedMessage.contains("unauthorized"))
}

internal fun Throwable.toUserFacingLoadMessage(fallbackMessage: String): String {
    return if (isUnauthorizedApiFailure()) {
        SESSION_REAUTH_REQUIRED_MESSAGE
    } else {
        message ?: fallbackMessage
    }
}
