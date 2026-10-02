package sk.uss.isac.chat.mobile.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import sk.uss.isac.chat.mobile.core.session.UserSession
import java.nio.file.Files
import java.util.Base64

class OwnedPreviewLoaderTest {
    @Test fun `owner switch during preview response releases no file`() {
        val root = Files.createTempDirectory("preview-switch").toFile()
        try { MockWebServer().use { server ->
            server.start()
            var session: UserSession? = UserSession(server.url("/chat/").toString(), "wss://invalid/ws",
                "e." + Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"A","iss":"issuer","tenant":"one"}""".toByteArray()) + ".s",
                profileApiUrl = server.url("/backend/").toString(), xApiType = "private")
            val client = OkHttpClient.Builder().addInterceptor { chain -> session = null; chain.proceed(chain.request()) }.build()
            server.enqueue(MockResponse().setBody("private A"))
            assertNull(OwnedPreviewLoader(root, client) { session }.load(server.url("/chat/preview/42").toString(), 42))
            assertEquals(0, root.walk().count { it.isFile })
        } } finally { root.deleteRecursively() }
    }

    @Test fun `colliding ids always authorize and return correct owner deployment and content`() {
        val root = Files.createTempDirectory("owned-preview-test").toFile()
        try {
            MockWebServer().use { one -> MockWebServer().use { two ->
                one.start(); two.start()
                fun owner(server: MockWebServer, subject: String) = UserSession(server.url("/chat/").toString(), "wss://invalid/ws",
                    "e." + Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$subject","iss":"issuer","tenant":"one"}""".toByteArray()) + ".s",
                    profileApiUrl = server.url("/backend/").toString(), xApiType = "private")
                var session: UserSession? = owner(one, "A")
                val loader = OwnedPreviewLoader(root, OkHttpClient()) { session }
                one.enqueue(MockResponse().setBody("one-A"))
                val pathA = loader.load(one.url("/chat/preview/42").toString(), 42)!!
                assertEquals("one-A", java.io.File(pathA).readText())
                session = owner(two, "B")
                two.enqueue(MockResponse().setBody("two-B"))
                val pathB = loader.load(two.url("/chat/preview/42").toString(), 42)!!
                assertNotEquals(java.io.File(pathA).parent, java.io.File(pathB).parent)
                assertEquals("two-B", java.io.File(pathB).readText())
                two.enqueue(MockResponse().setBody("two-new-version"))
                assertEquals("two-new-version", java.io.File(loader.load(two.url("/chat/preview/42").toString(), 42)!!).readText())
                assertEquals(1, java.io.File(pathB).parentFile.listFiles()?.count { it.isFile })
                two.enqueue(MockResponse().setResponseCode(403))
                assertNull(loader.load(two.url("/chat/preview/42").toString(), 42))
                assertFalse(java.io.File(pathB).exists())
                session = null
                assertNull(loader.load(two.url("/chat/preview/42").toString(), 42))
                assertEquals(1, one.requestCount)
                assertEquals(3, two.requestCount)
            } }
        } finally { root.deleteRecursively() }
    }
}
