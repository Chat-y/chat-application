package com.example.chat

import com.example.chat.data.api.ApiAuthResult
import com.example.chat.data.api.ApiAuthV2Client
import com.example.chat.data.api.ApiHttpTransport
import com.example.chat.data.api.ApiScopeChangedException
import com.example.chat.data.auth.v2.AuthV2AdoptionSink
import com.example.chat.data.auth.v2.AuthV2FlowController
import com.example.chat.data.auth.v2.AuthV2FlowException
import com.example.chat.data.auth.v2.InvitationVerification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AuthV2FlowControllerTest {

    private data class Call(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

    private class Transport : ApiHttpTransport {
        val calls = mutableListOf<Call>()
        var status = 200
        var response = """{"success":true,"data":{}}"""
        var after: () -> Unit = {}
        override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            calls += Call(method, url, headers, body)
            after()
            return status to response
        }
    }

    private val proof = mapOf(
        "x-device-id" to "id", "x-device-public-key" to "public",
        "x-device-challenge-id" to "challenge", "x-device-proof" to "proof",
    )

    private inner class Harness(current: () -> Boolean = { true }) {
        val transport = Transport()
        val adopted = mutableListOf<ApiAuthResult>()
        var proofCalls = 0
        val controller: AuthV2FlowController
        init {
            val client = ApiAuthV2Client("https://test.invalid", "qa", transport, "access", current, { true })
            controller = AuthV2FlowController(
                client = client,
                deviceProof = { proofCalls++; proof },
                adoptionSink = AuthV2AdoptionSink { adopted += it },
                isCurrent = current,
                io = Dispatchers.Unconfined,
            )
        }
    }

    private fun sessionResponse(rememberMe: Boolean = false) =
        """{"data":{"status":"success","token":"t","refreshToken":"r","user":{"id":"u-1","username":"maya"},
            "session":{"remember_me":$rememberMe,"refresh_expires_in_days":${if (rememberMe) 180 else 1}}}}"""

    @Test fun productionFlagOffMeansZeroRequests() {
        val transport = Transport()
        val client = ApiAuthV2Client("https://test.invalid", "qa", transport)
        val controller = AuthV2FlowController(client, { proof }, AuthV2AdoptionSink { }, io = Dispatchers.Unconfined)
        try {
            runBlocking { controller.login("maya", "password9", false) }
            fail("disabled expected")
        } catch (_: ApiAuthV2Client.Disabled) { }
        assertTrue(transport.calls.isEmpty())
        assertFalse(ReleaseFeatures.AUTH_V2_CONTRACTS)
    }

    @Test fun registerKeepsOneIdempotencyKeyAcrossStartRetryAndComplete() = runBlocking {
        val harness = Harness()
        harness.transport.status = 202
        harness.transport.response = """{"data":{"status":"registration_received","expires_in_minutes":10}}"""
        val start = harness.controller.startRegister("maya@example.com", "maya", "password9", "+12025550123")
        assertEquals(10, start.expiresInMinutes)
        harness.controller.retryRegisterStart()
        harness.controller.startRegister("maya@example.com", "maya", "password9", "+12025550123")
        harness.transport.status = 200
        harness.transport.response = sessionResponse()
        val completion = harness.controller.completeRegister("123 456")
        assertTrue(completion is AuthV2FlowController.AuthV2RegisterCompletion.Adopted)
        val keys = harness.transport.calls.map { it.headers["Idempotency-Key"] }.distinct()
        assertEquals(1, keys.size)
        val completeBody = JSONObject(harness.transport.calls.last().body!!)
        assertEquals("123456", completeBody.getString("code"))
        assertEquals("proof", harness.transport.calls.last().headers["x-device-proof"])
        assertEquals(1, harness.adopted.size)
        assertEquals("u-1", harness.adopted[0].user.id)
        assertNull(harness.controller.pendingRegisterEmail)
    }

    @Test fun pendingApprovalSkipsAdoptionAndClearsContext() = runBlocking {
        val harness = Harness()
        harness.transport.status = 202
        harness.transport.response = """{"data":{"status":"registration_received"}}"""
        harness.controller.startRegister("maya@example.com", "maya", "password9", "+12025550123")
        assertEquals("maya@example.com", harness.controller.pendingRegisterEmail)
        harness.transport.response =
            """{"data":{"status":"pending_approval","approval_id":"a-1","email":"maya@example.com"}}"""
        val completion = harness.controller.completeRegister("123456")
        assertTrue(completion is AuthV2FlowController.AuthV2RegisterCompletion.PendingApproval)
        assertTrue(harness.adopted.isEmpty())
        assertNull(harness.controller.pendingRegisterEmail)
    }

    @Test fun loginAdoptsRememberMeSessionThroughSink() = runBlocking {
        val harness = Harness()
        harness.transport.response = sessionResponse(rememberMe = true)
        val info = harness.controller.login("maya", "password9", rememberMe = true)
        assertTrue(info.rememberMe)
        assertEquals(180L, info.refreshExpiresInDays)
        val body = JSONObject(harness.transport.calls[0].body!!)
        assertTrue(body.getBoolean("remember_me"))
        assertEquals(1, harness.adopted.size)
        assertEquals("t", harness.adopted[0].token)
        assertEquals("r", harness.adopted[0].refreshToken)
    }

    @Test fun resetWithoutSignInNeitherProvesNorAdopts() = runBlocking {
        val harness = Harness()
        harness.transport.response = """{"data":{"password_updated":true,"sessions_revoked":true,"signed_in":false}}"""
        val result = harness.controller.resetPassword("maya@example.com", "123 456", "password10", signIn = false)
        assertTrue(result is AuthV2FlowController.AuthV2ResetResult.PasswordUpdated)
        assertEquals(0, harness.proofCalls)
        assertTrue(harness.adopted.isEmpty())
    }

    @Test fun resetWithSignInProvesAndAdopts() = runBlocking {
        val harness = Harness()
        harness.transport.response = sessionResponse()
        val result = harness.controller.resetPassword("maya@example.com", "123456", "password10", signIn = true)
        assertTrue(result is AuthV2FlowController.AuthV2ResetResult.SignedIn)
        assertEquals(1, harness.proofCalls)
        assertEquals(1, harness.adopted.size)
    }

    @Test fun lockoutMapsToFlowExceptionWithoutIdentifierEcho() = runBlocking {
        val harness = Harness()
        harness.transport.status = 429
        harness.transport.response = """{"detail":{"code":"RATE_LIMITED","message":"Too many attempts. Try again later."}}"""
        try {
            harness.controller.login("maya", "password9", false)
            fail("expected flow exception")
        } catch (expected: AuthV2FlowException) {
            assertEquals(429, expected.status)
            assertEquals("Too many attempts. Try again later.", expected.message)
            assertFalse(expected.message!!.contains("maya@"))
        }
        assertTrue(harness.adopted.isEmpty())
    }

    @Test fun scopeChangeAfterResponseBlocksAdoption() {
        var current = true
        val harness = Harness({ current })
        harness.transport.response = sessionResponse()
        harness.transport.after = { current = false }
        try {
            runBlocking { harness.controller.login("maya", "password9", false) }
            fail("expected scope exception")
        } catch (_: ApiScopeChangedException) { }
        assertTrue(harness.adopted.isEmpty())
    }

    @Test fun recoveryRoutesReturnServerMessageOnly() = runBlocking {
        val harness = Harness()
        harness.transport.response = """{"data":{"message":"If the address is registered, the username is on its way."}}"""
        val message = harness.controller.forgotUsername("maya@example.com")
        assertEquals("If the address is registered, the username is on its way.", message)
        harness.transport.response = """{"data":{"message":"If the address is registered, a verification link is on its way."}}"""
        assertTrue(harness.controller.sendVerificationLink("maya@example.com").contains("verification link"))
    }

    @Test fun workspaceSwitchAdoptsFreshSession() = runBlocking {
        val harness = Harness()
        harness.transport.response = sessionResponse()
        val info = harness.controller.switchWorkspace("other-workspace")
        assertEquals("t", harness.adopted[0].token)
        assertFalse(info.rememberMe)
        assertEquals("proof", harness.transport.calls[0].headers["x-device-proof"])
    }
    @Test fun workspaceAdoptionCarriesExplicitDestinationNotSourceTenant() = runBlocking {
        val transport=Transport().apply {response=sessionResponse()}
        val client=ApiAuthV2Client("https://test.invalid","qa",transport,"access",{true},{true})
        var destination:String?=null
        val controller=AuthV2FlowController(client,{proof},AuthV2AdoptionSink {fail("wrong generic adoption path")},io=Dispatchers.Unconfined,
            workspaceAdoption={result,workspace->assertEquals("u-1",result.user.id);destination=workspace})
        controller.switchWorkspace(" other-workspace ")
        assertEquals("other-workspace",destination)
    }

    @Test fun pendingLoginRoutesToApprovalWithoutAdoptingSession() = runBlocking {
        val harness=Harness()
        harness.transport.response="""{"data":{"status":"pending_approval","approval_id":"approval-1","email":"fixture@example.com"}}"""
        try {
            harness.controller.login("fixture@example.com","password9",false)
            fail("Expected pending approval")
        } catch(pending:com.example.chat.data.auth.v2.AuthV2PendingApprovalException) {
            assertEquals("approval-1",pending.approvalId)
            assertEquals("fixture@example.com",pending.email)
        }
        assertTrue(harness.adopted.isEmpty())
    }

    @Test fun invitationVerifyParsesOnlyValidBoundWorkspaceAndExpiry() = runBlocking {
        val h=Harness()
        h.transport.response="""{"data":{"valid":true,"status":"valid","expires_at":"2035-01-01T00:00:00Z","one_use":true,"redeemed":false,"tenant":{"tenant_id":"qa","name":"QA"},"bound_email":"fixture@example.com"}}"""
        val result=h.controller.verifyInvitation("test-token-abcdefghijkl","qa") as InvitationVerification.Valid
        assertEquals("qa",result.workspace);assertEquals("fixture@example.com",result.boundEmail)
        assertTrue(h.adopted.isEmpty())
        assertTrue(h.transport.calls.last().url.endsWith("/auth/invites/test-token-abcdefghijkl/verify"))
        h.transport.response="""{"data":{"valid":false,"status":"invalid","reason":"expired"}}"""
        assertSame(InvitationVerification.Expired,h.controller.verifyInvitation("test-token-abcdefghijkl",null))
        h.transport.response="""{"data":{"valid":false,"status":"invalid","reason":"redeemed"}}"""
        assertSame(InvitationVerification.Redeemed,h.controller.verifyInvitation("test-token-abcdefghijkl",null))
    }
    @Test fun invitationTokenRemainsInExactRegistrationRetryPayload() = runBlocking {
        val h=Harness()
        h.transport.response="""{"data":{"status":"registration_received","username_alternatives":[]}}"""
        h.controller.startRegister("fixture@example.com","fixture","password9","+12025550123",inviteToken="test-token-abcdefghijkl")
        h.controller.retryRegisterStart()
        val first=org.json.JSONObject(h.transport.calls[h.transport.calls.size-2].body!!)
        val retry=org.json.JSONObject(h.transport.calls.last().body!!)
        assertEquals("test-token-abcdefghijkl",first.getString("invite_token"))
        assertEquals(first.toString(),retry.toString())
    }

    @Test fun whitespaceInvitationTokenIsRejectedBeforeTransport() = runBlocking {
        val h=Harness()
        try {h.controller.verifyInvitation("abcdefghijklmnop qrst",null);fail("Invalid token accepted")}
        catch(_:IllegalArgumentException) { }
        assertTrue(h.transport.calls.isEmpty())
    }

    @Test fun invalidInvitationAlphabetAndOverLimitNeverReachTransport() = runBlocking {
        val h=Harness()
        for(token in listOf("a".repeat(513),"abcdefghijklmnop/","abcdefghijklmnop+","abcdefghijklmnop=")) {
            try {h.controller.verifyInvitation(token,null);fail("Invalid token accepted")}
            catch(_:IllegalArgumentException) { }
        }
        assertTrue(h.transport.calls.isEmpty())
        h.transport.response="""{"data":{"valid":false,"status":"invalid","reason":"expired"}}"""
        assertSame(InvitationVerification.Expired,h.controller.verifyInvitation("a".repeat(512),null))
    }

    @Test fun invalidInviteTokenNeverReachesRegistrationTransport() = runBlocking {
        val h=Harness()
        try {h.controller.startRegister("fixture@example.com","fixture","password9","+12025550123",inviteToken="bad/token");fail("Invalid invite token accepted")}
        catch(_:IllegalArgumentException) { }
        assertTrue(h.transport.calls.isEmpty())
    }
}
