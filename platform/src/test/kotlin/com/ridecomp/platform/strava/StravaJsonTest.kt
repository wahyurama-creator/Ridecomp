package com.ridecomp.platform.strava

import com.ridecomp.core.model.StravaAccount
import com.ridecomp.core.model.StravaTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StravaJsonTest {
    @Test
    fun `token reply with athlete`() {
        val grant = StravaJson.grant(
            """{"token_type":"Bearer","expires_at":1568775134,"expires_in":21600,"refresh_token":"r1",
               "access_token":"a1","athlete":{"id":1,"firstname":"Budi","lastname":"Santoso"}}""",
        )
        assertEquals(StravaTokens("a1", "r1", 1568775134), grant?.tokens)
        assertEquals("Budi Santoso", grant?.athleteName)
    }

    @Test
    fun `refresh reply without athlete`() {
        val grant = StravaJson.grant("""{"access_token":"a2","refresh_token":"r2","expires_at":10}""")
        assertEquals("a2", grant?.tokens?.accessToken)
        assertNull(grant?.athleteName)
    }

    @Test
    fun `invalid replies are rejected, not thrown`() {
        assertNull(StravaJson.grant("not json"))
        assertNull(StravaJson.grant("""{"access_token":"a","expires_at":10}"""))
        assertNull(StravaJson.grant("""{"access_token":null,"refresh_token":"r","expires_at":10}"""))
        assertNull(StravaJson.grant("""{"access_token":"a","refresh_token":"r","expires_at":0}"""))
        assertNull(StravaJson.grant("""{"access_token":"${"x".repeat(600)}","refresh_token":"r","expires_at":1}"""))
    }

    @Test
    fun `account round trip`() {
        val account = StravaAccount(
            tokens = StravaTokens("a", "r", 99),
            athleteName = "Rider",
            pendingState = "st",
        )
        assertEquals(account, StravaJson.decodeAccount(StravaJson.encodeAccount(account)))
        assertEquals(StravaAccount(), StravaJson.decodeAccount(StravaJson.encodeAccount(StravaAccount())))
        assertNull(StravaJson.decodeAccount("{"))
        // A file written by the personal-app version: the old app fields are simply ignored.
        assertEquals(
            StravaAccount(athleteName = "R"),
            StravaJson.decodeAccount("""{"client_id":"1","client_secret":"s","athlete_name":"R"}"""),
        )
    }
}
