package sk.uss.isac.chat.mobile.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import sk.uss.isac.chat.mobile.app.IsacChatMobileApplication

class IsacFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val appGraph = (application as? IsacChatMobileApplication)?.appGraph ?: return
        appGraph.pushTokenSyncCoordinator.onPushTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val appGraph = (application as? IsacChatMobileApplication)?.appGraph ?: return
        val payload = appGraph.pushPayloadParser.parse(message)
        appGraph.appPushEventCoordinator.publish(payload)
        appGraph.notificationCoordinator.showPushNotification(payload)
    }
}
