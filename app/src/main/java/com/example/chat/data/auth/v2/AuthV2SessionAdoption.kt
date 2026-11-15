package com.example.chat.data.auth.v2

import com.example.chat.data.api.ApiAuthResult
import com.example.chat.data.api.ApiAuthV2Client
import com.example.chat.data.api.ApiUserProfile
import org.json.JSONObject

/** Session adoption for the additive auth v2 contracts.
 *
 *  A v2 response becomes a signed-in session ONLY through the existing
 *  registered-account adoption gate: the same gate legacy login, register
 *  and add-account use on ApiBackendService (multi-account registration,
 *  routing switch, atomic token/user/refresh persistence, fail-closed on
 *  secure-storage failure). This file never publishes a session by itself;
 *  it validates the v2 payload, converts it to [ApiAuthResult], and hands
 *  it to an [AuthV2AdoptionSink] that the integrator wires to that gate.
 *
 *  Validation is fail-closed: any contract violation throws
 *  [AuthV2AdoptionException] and nothing is adopted. Messages never carry
 *  tokens, codes, or typed identifiers. */

/** Adoption could not proceed; the session was NOT established. */
class AuthV2AdoptionException(message: String) : IllegalStateException(message)

/** Where a validated v2 session goes. Production wiring (when
 *  AUTH_V2_CONTRACTS flips on) must delegate to ApiBackendService's existing
 *  registered-account adoption path; tests substitute a recorder. */
fun interface AuthV2AdoptionSink {
    suspend fun adopt(result: ApiAuthResult)
}

/** What a session-issuing v2 response turned into. */
sealed class AuthV2AuthOutcome {
    /** Full session: adopt [result] through the sink. */
    data class Session(val result: ApiAuthResult, val info: AuthV2SessionInfo) : AuthV2AuthOutcome()

    /** Workspace approval is on: no session exists yet (item 42). */
    data class PendingApproval(val approvalId: String, val email: String) : AuthV2AuthOutcome()
}

object AuthV2SessionAdoption {

    /** Classifies a register-complete / login / workspace-switch response.
     *  Throws [AuthV2AdoptionException] on any other status. */
    fun outcomeOf(result: ApiAuthV2Client.Result, emailForApproval: String? = null): AuthV2AuthOutcome {
        val data = result.data
        return when (data.optString("status").ifBlank { if (data.optStringOrNull("token") != null) "success" else "" }) {
            "success" -> AuthV2AuthOutcome.Session(toAuthResult(data), AuthV2Models.sessionInfo(data))
            "pending_approval" -> {
                val approvalId = data.optStringOrNull("approval_id")
                    ?: throw AuthV2AdoptionException("The approval response is incomplete. Try again.")
                val email = data.optStringOrNull("email") ?: emailForApproval
                    ?: throw AuthV2AdoptionException("The approval response is incomplete. Try again.")
                AuthV2AuthOutcome.PendingApproval(approvalId, email)
            }
            else -> throw AuthV2AdoptionException("The server did not complete sign-in. Try again.")
        }
    }

    /** Validates a v2 success payload and builds the session tuple the
     *  existing gate consumes. Requires a non-blank access token, a
     *  resolvable user id, and a non-blank refresh token (v2 sessions are
     *  refresh-based; adopting without one would strand the account at
     *  first expiry). */
    fun toAuthResult(data: JSONObject): ApiAuthResult {
        val token = data.optStringOrNull("token")
            ?: throw AuthV2AdoptionException("The server did not return a session. Try again.")
        val refresh = data.optStringOrNull("refreshToken")
            ?: data.optStringOrNull("refresh_token")
            ?: throw AuthV2AdoptionException("The server returned an incomplete session. Try again.")
        val userObj = data.optJSONObject("user")
            ?: throw AuthV2AdoptionException("The server returned an incomplete profile. Try again.")
        val userId = userObj.optStringOrNull("id")
            ?: userObj.optStringOrNull("uid")
            ?: userObj.optStringOrNull("sub")
            ?: throw AuthV2AdoptionException("The server returned an incomplete profile. Try again.")
        val username = userObj.optStringOrNull("username") ?: ""
        val displayName = userObj.optStringOrNull("display_name")
            ?: userObj.optStringOrNull("displayName")
            ?: userObj.optStringOrNull("name")
            ?: username.ifBlank { "User" }
        val initials = userObj.optStringOrNull("avatar_initials")
            ?: displayName.split(Regex("\\s+")).filter { it.isNotEmpty() }
                .take(2).joinToString("") { it.substring(0, 1).uppercase() }
                .ifBlank { "U" }
        return ApiAuthResult(
            token = token,
            refreshToken = refresh,
            user = ApiUserProfile(
                id = userId,
                username = username,
                displayName = displayName,
                avatarInitials = initials,
                avatarColorHex = userObj.optStringOrNull("avatar_color_hex")
                    ?: userObj.optStringOrNull("avatarColorHex") ?: "0xFF6366F1",
                about = userObj.optStringOrNull("about") ?: "",
                email = userObj.optStringOrNull("email") ?: "",
                phone = userObj.optStringOrNull("phone") ?: userObj.optStringOrNull("mobile") ?: "",
                avatarUrl = userObj.optStringOrNull("avatar_url") ?: userObj.optStringOrNull("avatarUrl"),
                bannerUrl = userObj.optStringOrNull("banner_url"),
                emailVerified = userObj.optBoolean("email_verified", false),
                phoneVerified = userObj.opt("phone_verified") == true,
            ),
        )
    }

    /** Validates, converts and adopts a session-issuing response.
     *  Returns the signed-in user id on adoption, or null when the account
     *  is waiting for workspace approval (no session exists). */
    suspend fun adopt(
        result: ApiAuthV2Client.Result,
        sink: AuthV2AdoptionSink,
        emailForApproval: String? = null,
    ): AuthV2AuthOutcome {
        val outcome = outcomeOf(result, emailForApproval)
        if (outcome is AuthV2AuthOutcome.Session) {
            sink.adopt(outcome.result)
        }
        return outcome
    }
}
