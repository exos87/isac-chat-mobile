package sk.uss.isac.chat.mobile.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class JwtPayloadReaderTest {

    @Test
    fun `reads subject and exp claims from jwt payload`() {
        val token = buildJwt(
            payload = """{"sub":"subject-123","preferred_username":"technik","exp":1780920000}"""
        )

        assertEquals("subject-123", JwtPayloadReader.readStringClaim(token, "sub"))
        assertEquals(1_780_920_000_000L, JwtPayloadReader.readExpMillis(token))
    }

    @Test
    fun `returns null for malformed token payload`() {
        assertNull(JwtPayloadReader.readStringClaim("bad-token", "sub"))
        assertNull(JwtPayloadReader.readExpMillis("bad-token"))
    }

    private fun buildJwt(payload: String): String {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray(StandardCharsets.UTF_8))
        val encodedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        return "$header.$encodedPayload.signature"
    }
}
