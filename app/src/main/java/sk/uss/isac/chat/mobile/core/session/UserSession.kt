package sk.uss.isac.chat.mobile.core.session

data class UserSession(
    val baseUrl: String,
    val wsUrl: String,
    val accessToken: String,
    val xApiType: String
)

