package sk.uss.isac.chat.mobile.core.data.model

data class ChatPushTestResult(
    val requested: Boolean,
    val delivered: Boolean,
    val registeredDeviceCount: Int,
    val summary: String
)
