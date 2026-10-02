package sk.uss.isac.chat.mobile.core.network

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import okhttp3.OkHttpClient
import okhttp3.Request
import sk.uss.isac.chat.mobile.core.session.UserSession
import sk.uss.isac.chat.mobile.core.session.localOwnerKey
import sk.uss.isac.chat.mobile.core.session.ownerDigest

/** Reauthorizes every load and never falls back to previously downloaded content. */
internal class OwnedPreviewLoader(
    private val cacheRoot: File,
    private val client: OkHttpClient,
    private val sessionProvider: () -> UserSession?
) {
    fun load(url: String, attachmentId: Long): String? {
        val session = sessionProvider() ?: return null
        val owner = session.localOwnerKey() ?: return null
        val directory = File(cacheRoot, owner)
        val target = File(directory, ownerDigest("$attachmentId\u0000$url") + ".preview")
        var temporary: File? = null
        fun ownsResponse() = sessionProvider()?.let {
            it.localOwnerKey() == owner && it.sessionEpoch == session.sessionEpoch
        } == true
        return runCatching {
            val request = Request.Builder().url(url).build()
            require(isSessionDestination(request.url, session)) { "Preview destination is outside session" }
            client.newCall(request.newBuilder().header("Authorization", "Bearer ${session.accessToken}")
                .header("X-Api-Type", session.xApiType).build()).execute().use { response ->
                check(response.isSuccessful) { "Preview denied" }
                val bytes = response.body?.bytes() ?: error("Empty preview")
                check(ownsResponse()) { "Preview owner changed" }
                directory.mkdirs()
                temporary = File.createTempFile("preview-", ".tmp", directory)
                temporary!!.writeBytes(bytes)
                check(ownsResponse()) { "Preview owner changed" }
                Files.move(temporary!!.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
                check(ownsResponse()) { "Preview owner changed" }
                target.absolutePath
            }
        }.getOrElse { temporary?.delete(); target.delete(); null }
    }
}
