package com.example.chat

import com.example.chat.data.api.ApiAuthV2Client
import com.example.chat.data.auth.v2.AuthV2AdoptionException
import com.example.chat.data.auth.v2.AuthV2Approval
import com.example.chat.data.auth.v2.AuthV2AuthOutcome
import com.example.chat.data.auth.v2.AuthV2Models
import com.example.chat.data.auth.v2.AuthV2SessionAdoption
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AuthV2SessionAdoptionTest {

    private fun successData(): JSONObject = JSONObject(
        """{"status":"success","token":"access-token","refreshToken":"refresh-token",
            "user":{"id":"user-1","username":"maya","display_name":"Maya B","email":"maya@example.com",
            "phone":"+12025550123","email_verified":true},
            "session":{"remember_me":true,"refresh_expires_in_days":180}}""")

    private fun resultOf(data: JSONObject, status: Int = 200) = ApiAuthV2Client.Result(status, data)

    @Test fun successMapsToAuthResultWithSessionInfo() {
        val outcome = AuthV2SessionAdoption.outcomeOf(resultOf(successData())) as AuthV2AuthOutcome.Session
        assertEquals("access-token", outcome.result.token)
        assertEquals("refresh-token", outcome.result.refreshToken)
        assertEquals("user-1", outcome.result.user.id)
        assertEquals("maya", outcome.result.user.username)
        assertEquals("Maya B", outcome.result.user.displayName)
        assertEquals("MB", outcome.result.user.avatarInitials)
        assertTrue(outcome.info.rememberMe)
        assertEquals(180L, outcome.info.refreshExpiresInDays)
    }

    @Test fun loginResponseWithoutStatusFieldStillMaps() {
        val loginShape = JSONObject("""{"token":"t","refreshToken":"r","user":{"uid":"u-9","username":"maya"}}""")
        val outcome = AuthV2SessionAdoption.outcomeOf(resultOf(loginShape)) as AuthV2AuthOutcome.Session
        assertEquals("u-9", outcome.result.user.id)
        assertEquals("t", outcome.result.token)
        assertFalse(outcome.info.rememberMe)
        assertEquals(1L, outcome.info.refreshExpiresInDays)
    }

    @Test fun missingTokenOrRefreshOrUserIdFailsClosed() {
        val noToken = JSONObject("""{"status":"success","refreshToken":"r","user":{"id":"u"}}""")
        val noRefresh = JSONObject("""{"status":"success","token":"t","user":{"id":"u"}}""")
        val noUserId = JSONObject("""{"status":"success","token":"t","refreshToken":"r","user":{"username":"maya"}}""")
        listOf(noToken, noRefresh, noUserId).forEach { bad ->
            try {
                AuthV2SessionAdoption.outcomeOf(resultOf(bad))
                fail("adoption must fail closed")
            } catch (expected: AuthV2AdoptionException) {
                assertFalse(expected.message!!.contains("maya"))
            }
        }
    }

    @Test fun pendingApprovalNeverAdopts() {
        val data = JSONObject("""{"status":"pending_approval","approval_id":"a-1","email":"maya@example.com"}""")
        val outcome = AuthV2SessionAdoption.outcomeOf(resultOf(data, 202))
        val pending = outcome as AuthV2AuthOutcome.PendingApproval
        assertEquals("a-1", pending.approvalId)
        assertEquals("maya@example.com", pending.email)
    }

    @Test fun unknownStatusFailsClosed() {
        val data = JSONObject("""{"status":"mystery","token":"t","refreshToken":"r","user":{"id":"u"}}""")
        try {
            AuthV2SessionAdoption.outcomeOf(resultOf(data))
            fail("unknown status must not adopt")
        } catch (expected: AuthV2AdoptionException) { }
    }

    @Test fun onboardingParsesBrandingTermsSsoAndPolicy() {
        val data = JSONObject("""{
            "workspace":{"name":"QA Chat","tenant_id":"qa","allow_user_registration":true},
            "branding":{"display_name":"QA Chat","accent_color":"#123456","background_color":"bad"},
            "contact_admin":{"available":true,"email":"admin@example.com"},
            "registration":{"account_approval_required":true,"discoverable":true,"allowed_email_domains":["example.com"]},
            "terms":{"required":true,"version":"2026-11","url":"https://t","privacy_url":"https://p"},
            "sso":{"enabled":true,"providers":[{"id":"oidc-main","type":"oidc","name":"Company SSO","enabled":true}]},
            "contracts":{"login":"/auth/login/v2"}}""")
        val onboarding = AuthV2Models.onboarding(data)
        assertEquals("qa", onboarding.tenantId)
        assertTrue(onboarding.allowUserRegistration)
        assertEquals(0xFF123456L, onboarding.branding.accentColorArgb())
        assertNull(onboarding.branding.backgroundColorArgb())
        assertTrue(onboarding.contactAdmin.available)
        assertTrue(onboarding.registration.accountApprovalRequired)
        assertEquals(listOf("example.com"), onboarding.registration.allowedEmailDomains)
        assertEquals("2026-11", onboarding.terms.version)
        assertEquals(1, onboarding.ssoProviders.size)
        assertEquals("oidc-main", onboarding.ssoProviders[0].id)
    }

    @Test fun approvalStatesAndReasonParse() {
        val pending = AuthV2Models.approval(JSONObject("""{"approval_id":"a","approval_state":"pending","reason":null}"""))
        assertEquals(AuthV2Approval.State.PENDING, pending.state)
        val rejected = AuthV2Models.approval(JSONObject("""{"approval_id":"a","approval_state":"rejected","reason":"No contractors"}"""))
        assertEquals(AuthV2Approval.State.REJECTED, rejected.state)
        assertEquals("No contractors", rejected.reason)
        val weird = AuthV2Models.approval(JSONObject("""{"approval_id":"a","approval_state":"paused"}"""))
        assertEquals(AuthV2Approval.State.UNKNOWN, weird.state)
    }
}
