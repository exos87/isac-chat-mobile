package sk.uss.isac.chat.mobile.core.notifications

data class PushMessagingDiagnostics(
    val packageName: String,
    val firebaseConfigured: Boolean,
    val tokenAvailable: Boolean,
    val tokenPreview: String?,
    val errorMessage: String?
)
