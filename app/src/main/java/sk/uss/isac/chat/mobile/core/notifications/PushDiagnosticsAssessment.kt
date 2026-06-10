package sk.uss.isac.chat.mobile.core.notifications

data class PushDiagnosticsAssessment(
    val severity: PushDiagnosticsSeverity,
    val title: String,
    val message: String
)

enum class PushDiagnosticsSeverity {
    OK,
    WARNING,
    ERROR,
    UNKNOWN
}
