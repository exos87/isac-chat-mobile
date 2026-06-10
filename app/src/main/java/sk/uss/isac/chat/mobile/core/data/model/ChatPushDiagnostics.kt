package sk.uss.isac.chat.mobile.core.data.model

data class ChatPushDiagnostics(
    val enabled: Boolean,
    val configured: Boolean,
    val summary: String,
    val recommendedAction: String,
    val gateway: String,
    val missingRequirements: List<String>,
    val registeredDeviceCount: Int,
    val registeredPackages: List<String>,
    val currentRegistration: ChatPushRegistration?,
    val registrationHealth: String,
    val registrationSummary: String,
    val registrationIssues: List<String>,
    val deliveryHealth: String,
    val deliverySummary: String
)

data class ChatPushRegistration(
    val status: String,
    val pushProvider: String,
    val tokenPreview: String?,
    val pushTokenPresent: Boolean,
    val packageName: String?,
    val versionName: String?,
    val platform: String?,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val osVersion: String?,
    val sdkInt: Int?,
    val lastSource: String?,
    val verifiedAt: String?,
    val pushTokenUpdatedAt: String?,
    val lastPushAttemptedAt: String?,
    val lastPushDeliveredAt: String?,
    val lastPushError: String?
)
