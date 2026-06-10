package sk.uss.isac.chat.mobile.core.notifications

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import sk.uss.isac.chat.mobile.core.data.model.ChatPushDiagnostics
import sk.uss.isac.chat.mobile.core.data.model.ChatPushRegistration

object PushDiagnosticsSummaryFormatter {

    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("d. M. yyyy HH:mm:ss", Locale("sk", "SK"))

    fun formatBackendDiagnostics(diagnostics: ChatPushDiagnostics): String {
        val packages = diagnostics.registeredPackages.takeIf { it.isNotEmpty() }
            ?.joinToString()
            ?: "žiadne"
        val missingRequirements = diagnostics.missingRequirements.takeIf { it.isNotEmpty() }
            ?.joinToString()
        val registrationIssues = diagnostics.registrationIssues.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n") { "- $it" }

        return buildString {
            appendLine("Push stav: ${diagnostics.summary}")
            if (diagnostics.recommendedAction.isNotBlank()) {
                appendLine("Odporúčaná akcia: ${diagnostics.recommendedAction}")
            }
            appendLine("Brána: ${diagnostics.gateway.ifBlank { "nezistená" }}")
            appendLine("Registrované zariadenia: ${diagnostics.registeredDeviceCount}")
            if (missingRequirements != null) {
                appendLine("Chýbajúce podmienky: $missingRequirements")
            }
            appendLine("Zdravie registrácie: ${diagnostics.registrationHealth}")
            if (diagnostics.registrationSummary.isNotBlank()) {
                appendLine("Vyhodnotenie: ${diagnostics.registrationSummary}")
            }
            appendLine("Doručovanie: ${diagnostics.deliveryHealth}")
            if (diagnostics.deliverySummary.isNotBlank()) {
                appendLine("Doručenie: ${diagnostics.deliverySummary}")
            }
            diagnostics.currentRegistration?.let { registration ->
                appendRegistration(this, registration)
            }
            if (registrationIssues != null) {
                appendLine("Problémy registrácie:")
                appendLine(registrationIssues)
            }
            append("Balíky: $packages")
        }
    }

    fun assess(
        backendDiagnostics: ChatPushDiagnostics?,
        localDiagnostics: PushMessagingDiagnostics,
        registrationState: PushRegistrationState
    ): PushDiagnosticsAssessment {
        if (backendDiagnostics == null) {
            return when {
                registrationState.lastSyncError != null -> PushDiagnosticsAssessment(
                    severity = PushDiagnosticsSeverity.WARNING,
                    title = "Push vyžaduje pozornosť",
                    message = "Backend diagnostika ešte nebola načítaná a posledný sync tokenu zlyhal."
                )
                localDiagnostics.tokenAvailable -> PushDiagnosticsAssessment(
                    severity = PushDiagnosticsSeverity.UNKNOWN,
                    title = "Push čaká na overenie",
                    message = "Zariadenie má lokálny FCM token, ale backend diagnostika ešte nebola načítaná."
                )
                !localDiagnostics.firebaseConfigured -> PushDiagnosticsAssessment(
                    severity = PushDiagnosticsSeverity.WARNING,
                    title = "Firebase nie je pripravený",
                    message = "Mobil nemá lokálnu Firebase konfiguráciu, preto push nemôže fungovať."
                )
                else -> PushDiagnosticsAssessment(
                    severity = PushDiagnosticsSeverity.UNKNOWN,
                    title = "Push stav nie je známy",
                    message = "Načítaj backend push diagnostiku alebo spusti synchronizáciu tokenu."
                )
            }
        }

        if (!backendDiagnostics.enabled) {
            return PushDiagnosticsAssessment(
                severity = PushDiagnosticsSeverity.WARNING,
                title = "Push je vypnutý",
                message = backendDiagnostics.recommendedAction.ifBlank {
                    "Chat push notifikácie sú v tomto prostredí vypnuté."
                }
            )
        }

        if (!backendDiagnostics.configured) {
            return PushDiagnosticsAssessment(
                severity = PushDiagnosticsSeverity.ERROR,
                title = "Push nie je nakonfigurovaný",
                message = backendDiagnostics.recommendedAction.ifBlank {
                    "Backend nemá kompletnú konfiguráciu pre chat push notifikácie."
                }
            )
        }

        if (backendDiagnostics.registrationHealth == "ERROR" || backendDiagnostics.deliveryHealth == "ERROR") {
            return PushDiagnosticsAssessment(
                severity = PushDiagnosticsSeverity.ERROR,
                title = "Push hlási chybu",
                message = backendDiagnostics.recommendedAction.ifBlank {
                    backendDiagnostics.deliverySummary.ifBlank {
                        backendDiagnostics.registrationSummary.ifBlank {
                            "Push registrácia alebo doručovanie je v chybovom stave."
                        }
                    }
                }
            )
        }

        val consistencySummary = formatConsistency(
            backendDiagnostics = backendDiagnostics,
            localDiagnostics = localDiagnostics,
            registrationState = registrationState
        )
        if (
            backendDiagnostics.registrationHealth == "WARNING" ||
            backendDiagnostics.deliveryHealth == "WARNING" ||
            !consistencySummary.isNullOrBlank() &&
            !consistencySummary.startsWith("Lokálny stav zariadenia a backend registrácia sú konzistentné.")
        ) {
            return PushDiagnosticsAssessment(
                severity = PushDiagnosticsSeverity.WARNING,
                title = "Push vyžaduje kontrolu",
                message = backendDiagnostics.recommendedAction.ifBlank {
                    consistencySummary ?: backendDiagnostics.registrationSummary.ifBlank {
                        backendDiagnostics.deliverySummary.ifBlank {
                            "Push vrstva funguje len čiastočne a potrebuje kontrolu."
                        }
                    }
                }
            )
        }

        return PushDiagnosticsAssessment(
            severity = PushDiagnosticsSeverity.OK,
            title = "Push je pripravený",
            message = backendDiagnostics.deliverySummary.ifBlank {
                "Registrácia zariadenia aj doručovanie vyzerajú v poriadku."
            }
        )
    }

    fun formatLocalDiagnostics(diagnostics: PushMessagingDiagnostics): String {
        return buildString {
            appendLine("Balík appky: ${diagnostics.packageName}")
            appendLine("Firebase konfigurácia: ${if (diagnostics.firebaseConfigured) "áno" else "nie"}")
            appendLine("Lokálny FCM token: ${if (diagnostics.tokenAvailable) "dostupný" else "nedostupný"}")
            diagnostics.tokenPreview?.let { appendLine("Token preview: $it") }
            diagnostics.errorMessage?.let { append("Lokálna chyba: $it") }
        }.trim()
    }

    fun formatTokenSync(state: PushRegistrationState): String? {
        val parts = mutableListOf<String>()
        state.currentToken?.let { token ->
            val tokenPreview = if (token.length <= 16) token else "${token.take(8)}...${token.takeLast(8)}"
            parts += "Aktuálny token: $tokenPreview"
        }
        state.lastSyncedAtEpochMillis?.let { parts += "Posledný sync: ${formatTimestamp(it)}" }
        state.lastSyncedSubject?.let { parts += "Subjekt: $it" }
        state.lastSyncError?.let { parts += "Posledná chyba syncu: $it" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    fun formatPushTest(state: PushRegistrationState): String? {
        val testedAt = state.lastPushTestAtEpochMillis ?: return null
        val summary = state.lastPushTestSummary ?: return null
        val status = when (state.lastPushTestDelivered) {
            true -> "doručený"
            false -> "nedoručený"
            null -> "neznámy"
        }
        return buildString {
            appendLine("Posledný test push: ${formatTimestamp(testedAt)}")
            appendLine("Výsledok: $status")
            append(summary)
        }
    }

    fun formatConsistency(
        backendDiagnostics: ChatPushDiagnostics?,
        localDiagnostics: PushMessagingDiagnostics,
        registrationState: PushRegistrationState
    ): String? {
        val issues = mutableListOf<String>()

        if (backendDiagnostics == null) {
            return if (localDiagnostics.tokenAvailable || registrationState.lastSyncedToken != null) {
                "Backend push diagnostika ešte nebola načítaná. Najprv použi tlačidlo 'Načítať stav push'."
            } else {
                null
            }
        }

        val backendRegistration = backendDiagnostics.currentRegistration
        if (!localDiagnostics.firebaseConfigured) {
            issues += "Firebase nie je lokálne nakonfigurovaný."
        }
        if (localDiagnostics.tokenAvailable && backendDiagnostics.registeredDeviceCount == 0) {
            issues += "Mobil má lokálny FCM token, ale backend neeviduje žiadne registrované zariadenie."
        }
        if (!localDiagnostics.tokenAvailable && backendDiagnostics.registeredDeviceCount > 0) {
            issues += "Backend eviduje zariadenie, ale lokálny FCM token nie je dostupný."
        }
        if (registrationState.lastSyncError != null) {
            issues += "Posledný sync tokenu zlyhal: ${registrationState.lastSyncError}"
        }
        if (
            backendRegistration?.packageName != null &&
            backendRegistration.packageName != localDiagnostics.packageName
        ) {
            issues += "Backend má registrovaný iný balík appky (${backendRegistration.packageName})."
        }
        if (backendDiagnostics.registrationHealth == "ERROR") {
            issues += "Backend hlási chybový stav registrácie."
        }

        return if (issues.isEmpty()) {
            "Lokálny stav zariadenia a backend registrácia sú konzistentné."
        } else {
            buildString {
                appendLine("Konzistencia push registrácie vyžaduje pozornosť:")
                append(issues.joinToString(separator = "\n") { "- $it" })
            }
        }
    }

    private fun appendRegistration(builder: StringBuilder, registration: ChatPushRegistration) {
        builder.appendLine(
            "Registrácia: ${registration.status.ifBlank { "nezistená" }} / " +
                registration.pushProvider.ifBlank { "bez providera" }
        )
        registration.packageName?.let { builder.appendLine("Balík zariadenia: $it") }
        registration.versionName?.let { builder.appendLine("Verzia appky: $it") }
        val deviceName = listOfNotNull(registration.deviceManufacturer, registration.deviceModel)
            .joinToString(" ")
            .ifBlank { "" }
        if (deviceName.isNotBlank()) {
            builder.appendLine("Zariadenie: $deviceName")
        }
        registration.osVersion?.let { osVersion ->
            val sdkLabel = registration.sdkInt?.let { " (SDK $it)" }.orEmpty()
            builder.appendLine("Android: $osVersion$sdkLabel")
        }
        registration.tokenPreview?.let { builder.appendLine("Token: $it") }
        registration.pushTokenUpdatedAt?.let { builder.appendLine("Aktualizácia tokenu: $it") }
        registration.lastPushAttemptedAt?.let { builder.appendLine("Posledný pokus o doručenie: $it") }
        registration.lastPushDeliveredAt?.let { builder.appendLine("Posledné úspešné doručenie: $it") }
        registration.lastPushError?.let { builder.appendLine("Posledná chyba: $it") }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return timestampFormatter.format(
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        )
    }
}
