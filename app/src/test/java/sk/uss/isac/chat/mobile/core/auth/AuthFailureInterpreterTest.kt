package sk.uss.isac.chat.mobile.core.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AuthFailureInterpreterTest {

    @Test
    fun `recognizes retrofit 401 as unauthorized api failure`() {
        val response = Response.error<String>(
            401,
            """{"message":"Unauthorized"}""".toResponseBody("application/json".toMediaType())
        )
        val error = HttpException(response)

        assertTrue(error.isUnauthorizedApiFailure())
        assertEquals(
            SESSION_REAUTH_REQUIRED_MESSAGE,
            error.toUserFacingLoadMessage("fallback")
        )
    }

    @Test
    fun `recognizes plain 401 unauthorized message`() {
        val error = IllegalStateException("Backend returned 401 Unauthorized")

        assertTrue(error.isUnauthorizedApiFailure())
        assertEquals(
            SESSION_REAUTH_REQUIRED_MESSAGE,
            error.toUserFacingLoadMessage("fallback")
        )
    }

    @Test
    fun `non auth failure keeps original message`() {
        val error = IllegalStateException("Connection refused")

        assertFalse(error.isUnauthorizedApiFailure())
        assertEquals("Connection refused", error.toUserFacingLoadMessage("fallback"))
    }
}
