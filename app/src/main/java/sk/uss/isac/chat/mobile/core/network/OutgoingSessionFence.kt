package sk.uss.isac.chat.mobile.core.network

import sk.uss.isac.chat.mobile.core.session.UserSession

/** Captured before IO/suspension, so a queued request cannot borrow a later login. */
data class OutgoingSessionFence(val session: UserSession)
