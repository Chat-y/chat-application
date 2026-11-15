package com.example.chat.data.auth.v2

/** The documented v2 client error surface. No identifiers or secrets are appended to copy. */
object AuthV2ErrorPolicy {
 enum class Action { FixInput, RequestCode, DeviceVerification, ContactAdmin, ReviewTerms, RetryLater, Restart, Generic }
 val documentedCodes:Map<String,Action> = mapOf(
  "INVITE_INVALID" to Action.Restart,"INVITE_EXPIRED" to Action.Restart,"INVITE_REDEEMED" to Action.Restart,
  "INVITE_WORKSPACE_MISMATCH" to Action.FixInput,"INVITE_EMAIL_MISMATCH" to Action.FixInput,
  "DEVICE_PROOF_REQUIRED" to Action.DeviceVerification,"INVALID_DEVICE_CHALLENGE" to Action.DeviceVerification,
  "INVALID_DEVICE_PROOF" to Action.DeviceVerification,"DEVICE_ACCOUNT_LIMIT" to Action.ContactAdmin,
  "WORKSPACE_UNAVAILABLE" to Action.FixInput,"INVALID_USERNAME" to Action.FixInput,"INVALID_PASSWORD" to Action.FixInput,
  "REGISTRATION_DISABLED" to Action.ContactAdmin,"EMAIL_DOMAIN_NOT_ALLOWED" to Action.ContactAdmin,
  "TERMS_VERSION_REQUIRED" to Action.ReviewTerms,"IDEMPOTENCY_KEY_REUSED" to Action.Restart,
  "INVALID_OR_EXPIRED_CODE" to Action.RequestCode,"REGISTRATION_NOT_COMPLETABLE" to Action.Restart,
  "USERNAME_UNAVAILABLE" to Action.FixInput,"PHONE_PROOF_PAIR_REQUIRED" to Action.FixInput,
  "INVALID_PHONE_PROOF" to Action.FixInput,"PHONE_PROOF_MISMATCH" to Action.FixInput,
  "APPROVAL_NOT_FOUND" to Action.ContactAdmin,"APPROVAL_ADMIN_DISABLED" to Action.ContactAdmin,
  "APPROVAL_ADMIN_UNAUTHORIZED" to Action.ContactAdmin,"INVALID_APPROVAL_DECISION" to Action.ContactAdmin,
  "INVALID_OR_EXPIRED_LINK" to Action.RequestCode,"SSO_PROVIDER_NOT_FOUND" to Action.ContactAdmin,
  "SSO_NOT_CONFIGURED" to Action.ContactAdmin,"SSO_CALLBACK_NOT_ENABLED" to Action.ContactAdmin,
  "RATE_LIMITED" to Action.RetryLater,"INVALID_RESPONSE" to Action.RetryLater)
 fun action(code:String?,status:Int):Action = documentedCodes[code] ?: when(status) {
  429->Action.RetryLater;in 500..599->Action.RetryLater;else->Action.Generic
 }
 fun message(code:String?,status:Int,serverMessage:String?):String = serverMessage?.takeIf(String::isNotBlank) ?: when(action(code,status)) {
  Action.FixInput->"Check the highlighted details and try again."
  Action.RequestCode->"That code or link is wrong or has expired. Request a new one."
  Action.DeviceVerification->"Installation verification is required. Start sign-in again."
  Action.ContactAdmin->"This step needs help from your workspace administrator."
  Action.ReviewTerms->"Please review the latest workspace terms before continuing."
  Action.RetryLater->"The request could not be completed. Your details are kept. Try again later."
  Action.Restart->"Start this step again. No account change was confirmed."
  Action.Generic->"The request could not be completed. Try again."
 }
}
