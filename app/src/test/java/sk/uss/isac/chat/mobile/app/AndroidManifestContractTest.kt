package sk.uss.isac.chat.mobile.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidManifestContractTest {

    @Test
    fun `main activity keeps single task launch mode for oidc callback reuse`() {
        val manifestFile = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() } ?: error("AndroidManifest.xml was not found.")
        val manifest = manifestFile.readText()

        assertTrue(manifest.contains("""android:name="sk.uss.isac.chat.mobile.app.MainActivity""""))
        assertTrue(manifest.contains("""android:launchMode="singleTask""""))
        assertTrue(manifest.contains("""android:host="auth""""))
        assertTrue(manifest.contains("""android:pathPrefix="/callback""""))
    }
}
