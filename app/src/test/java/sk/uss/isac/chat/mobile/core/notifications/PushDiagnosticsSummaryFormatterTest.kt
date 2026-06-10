package sk.uss.isac.chat.mobile.core.notifications

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import sk.uss.isac.chat.mobile.core.data.model.ChatPushDiagnostics
import sk.uss.isac.chat.mobile.core.data.model.ChatPushRegistration

class PushDiagnosticsSummaryFormatterTest {

    @Test
    fun `backend summary includes registration health and issues`() {
        val diagnostics = ChatPushDiagnostics(
            enabled = true,
            configured = true,
            summary = "ready",
            recommendedAction = "V mobilnej aplikacii otvorte servisnu obrazovku a spustite manualny resync tokenu.",
            gateway = "FCM",
            missingRequirements = emptyList(),
            registeredDeviceCount = 1,
            registeredPackages = listOf("sk.uss.isac.chat.mobile"),
            currentRegistration = ChatPushRegistration(
                status = "VERIFIED",
                pushProvider = "FCM",
                tokenPreview = "abc123...9999",
                pushTokenPresent = true,
                packageName = "sk.uss.isac.chat.mobile",
                versionName = "0.1.27",
                platform = "ANDROID",
                deviceManufacturer = "Samsung",
                deviceModel = "SM-S918B",
                osVersion = "14",
                sdkInt = 34,
                lastSource = "mobile-app",
                verifiedAt = "2026-03-28T12:00:00Z",
                pushTokenUpdatedAt = "2026-03-28T12:01:00Z",
                lastPushAttemptedAt = "2026-03-29T09:10:11Z",
                lastPushDeliveredAt = "2026-03-29T09:10:12Z",
                lastPushError = "UNREGISTERED"
            ),
            registrationHealth = "ERROR",
            registrationSummary = "Mobilna push registracia je evidovana, ale posledne dorucovanie zlyhalo.",
            registrationIssues = listOf("Posledna push chyba: UNREGISTERED"),
            deliveryHealth = "ERROR",
            deliverySummary = "Posledne push dorucovanie zlyhalo: UNREGISTERED"
        )

        val summary = PushDiagnosticsSummaryFormatter.formatBackendDiagnostics(diagnostics)

        assertTrue(summary.contains("Zdravie registr\u00e1cie: ERROR"))
        assertTrue(summary.contains("Odpor\u00fa\u010dan\u00e1 akcia: V mobilnej aplikacii otvorte servisnu obrazovku a spustite manualny resync tokenu."))
        assertTrue(summary.contains("Doru\u010dovanie: ERROR"))
        assertTrue(summary.contains("Doru\u010denie: Posledne push dorucovanie zlyhalo: UNREGISTERED"))
        assertTrue(summary.contains("Posledn\u00fd pokus o doru\u010denie: 2026-03-29T09:10:11Z"))
        assertTrue(summary.contains("Posledn\u00e9 \u00faspe\u0161n\u00e9 doru\u010denie: 2026-03-29T09:10:12Z"))
        assertTrue(summary.contains("Probl\u00e9my registr\u00e1cie:"))
        assertTrue(summary.contains("UNREGISTERED"))
    }

    @Test
    fun `local summary includes token preview and firebase state`() {
        val summary = PushDiagnosticsSummaryFormatter.formatLocalDiagnostics(
            PushMessagingDiagnostics(
                packageName = "sk.uss.isac.chat.mobile",
                firebaseConfigured = true,
                tokenAvailable = true,
                tokenPreview = "abc123...9999",
                errorMessage = null
            )
        )

        assertTrue(summary.contains("Bal\u00edk appky: sk.uss.isac.chat.mobile"))
        assertTrue(summary.contains("Firebase konfigur\u00e1cia: \u00e1no"))
        assertTrue(summary.contains("Token preview: abc123...9999"))
    }

    @Test
    fun `consistency summary flags local token without backend registration`() {
        val backendDiagnostics = ChatPushDiagnostics(
            enabled = true,
            configured = true,
            summary = "ready",
            recommendedAction = "Prihlaste sa v mobilnej aplikacii a spustite synchronizaciu push tokenu.",
            gateway = "FCM",
            missingRequirements = emptyList(),
            registeredDeviceCount = 0,
            registeredPackages = emptyList(),
            currentRegistration = null,
            registrationHealth = "WARNING",
            registrationSummary = "Chyba registracia.",
            registrationIssues = listOf("Chyba registracia modulu mobileApp v profile."),
            deliveryHealth = "UNKNOWN",
            deliverySummary = "Push dorucovanie este nema aktivnu registraciu zariadenia."
        )
        val localDiagnostics = PushMessagingDiagnostics(
            packageName = "sk.uss.isac.chat.mobile",
            firebaseConfigured = true,
            tokenAvailable = true,
            tokenPreview = "abc123...9999",
            errorMessage = null
        )

        val summary = PushDiagnosticsSummaryFormatter.formatConsistency(
            backendDiagnostics = backendDiagnostics,
            localDiagnostics = localDiagnostics,
            registrationState = PushRegistrationState()
        )

        assertTrue(summary!!.contains("backend neeviduje"))
    }

    @Test
    fun `assessment returns error when backend push is not configured`() {
        val diagnostics = ChatPushDiagnostics(
            enabled = true,
            configured = false,
            summary = "missing-config",
            recommendedAction = "Doplnte Firebase service account JSON do backendu.",
            gateway = "NOOP",
            missingRequirements = listOf("serviceAccount"),
            registeredDeviceCount = 0,
            registeredPackages = emptyList(),
            currentRegistration = null,
            registrationHealth = "UNKNOWN",
            registrationSummary = "Registracia nebola overena.",
            registrationIssues = emptyList(),
            deliveryHealth = "UNKNOWN",
            deliverySummary = "Dorucovanie sa este netestovalo."
        )

        val assessment = PushDiagnosticsSummaryFormatter.assess(
            backendDiagnostics = diagnostics,
            localDiagnostics = PushMessagingDiagnostics(
                packageName = "sk.uss.isac.chat.mobile",
                firebaseConfigured = true,
                tokenAvailable = true,
                tokenPreview = "abc123...9999",
                errorMessage = null
            ),
            registrationState = PushRegistrationState()
        )

        assertEquals(PushDiagnosticsSeverity.ERROR, assessment.severity)
        assertEquals("Push nie je nakonfigurovan\u00fd", assessment.title)
        assertTrue(assessment.message.contains("Firebase service account JSON"))
    }

    @Test
    fun `assessment returns warning when backend delivery is stale`() {
        val diagnostics = ChatPushDiagnostics(
            enabled = true,
            configured = true,
            summary = "ready",
            recommendedAction = "Poslite test push notifikaciu a overte dorucenie na zariadeni.",
            gateway = "FCM",
            missingRequirements = emptyList(),
            registeredDeviceCount = 1,
            registeredPackages = listOf("sk.uss.isac.chat.mobile"),
            currentRegistration = ChatPushRegistration(
                status = "VERIFIED",
                pushProvider = "FCM",
                tokenPreview = "abc123...9999",
                pushTokenPresent = true,
                packageName = "sk.uss.isac.chat.mobile",
                versionName = "0.1.35",
                platform = "ANDROID",
                deviceManufacturer = "Google",
                deviceModel = "sdk_gphone64_x86_64",
                osVersion = "15",
                sdkInt = 35,
                lastSource = "mobile-app",
                verifiedAt = "2026-03-20T12:00:00Z",
                pushTokenUpdatedAt = "2026-03-29T12:00:00Z",
                lastPushAttemptedAt = "2026-03-20T12:00:00Z",
                lastPushDeliveredAt = "2026-03-20T12:00:10Z",
                lastPushError = null
            ),
            registrationHealth = "OK",
            registrationSummary = "Mobilna push registracia je overena a pripravena na dorucovanie.",
            registrationIssues = emptyList(),
            deliveryHealth = "WARNING",
            deliverySummary = "Posledne uspesne push dorucenie je starsie ako 3 dni."
        )

        val assessment = PushDiagnosticsSummaryFormatter.assess(
            backendDiagnostics = diagnostics,
            localDiagnostics = PushMessagingDiagnostics(
                packageName = "sk.uss.isac.chat.mobile",
                firebaseConfigured = true,
                tokenAvailable = true,
                tokenPreview = "abc123...9999",
                errorMessage = null
            ),
            registrationState = PushRegistrationState()
        )

        assertEquals(PushDiagnosticsSeverity.WARNING, assessment.severity)
        assertEquals("Push vy\u017eaduje kontrolu", assessment.title)
        assertTrue(assessment.message.contains("Poslite test push"))
    }
}
