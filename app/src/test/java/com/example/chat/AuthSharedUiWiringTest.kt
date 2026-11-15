package com.example.chat
import java.io.File
import org.junit.Assert.*
import org.junit.Test
/** Source contracts only. Font/display matrix and visual layout need Android pixel checks. */
class AuthSharedUiWiringTest {
 private val root get() = if(File("app/src/main").exists()) File("app/src/main") else File("src/main")
 private fun t(p:String)=File(root,"kotlin/com/example/chat/"+p).readText()
 @Test fun bothScreensUseTopScrollScaffoldAndSharedToggle() {
  for(n in listOf("LoginScreen.kt","RegisterScreen.kt")) {
   val s=t("ui/auth/"+n);assertTrue(s.contains("ChatyAuthScreen(theme)"))
   assertFalse(Regex("[0-9]+\\.(dp|sp)\\b").containsMatchIn(s))
  }
  val c=t("ui/core/components/auth/ChatyAuthComponents.kt")
  assertTrue(c.contains("verticalScroll(rememberScrollState())"));assertTrue(c.contains("Alignment.TopCenter"))
  assertTrue(c.contains("auth-mode-slide"));assertTrue(c.contains("rememberChatyReduceMotion"))
  val toggle=c.substringAfter("fun ChatyModeToggle").substringBefore("fun ChatyBackButton")
  assertFalse(toggle.contains("Column("));assertTrue(toggle.contains("FittedAuthText(authCopy(label)"))
 }
 @Test fun credentialsHaveAutofillAndDoneUsesExistingHandlers() {
  val login=t("ui/auth/LoginScreen.kt");val register=t("ui/auth/RegisterScreen.kt")
  assertTrue(login.contains("onDone = { if (!isLoading) handleLogin() }"))
  assertTrue(register.contains("onDone = { if (!isLoading) handleRegister() }"))
  assertTrue(login.contains("listOf(\"Password\")"));assertTrue(register.contains("listOf(\"NewPassword\")"))
  val c=t("ui/auth/AuthComponents.kt")
  assertTrue(c.contains("requestAutofillForNode"));assertTrue(c.contains("keyboardType == KeyboardType.Password"))
  assertTrue(c.contains("Caps Lock is on"));assertTrue(t("ui/core/components/auth/AuthFormSupport.kt").contains("LiveRegionMode.Polite"))
  assertTrue(c.contains("enabled = enabled && !isLoading"))
 }
 @Test fun deviceActionAndSearchableCountryUiShareComponent() {
  val p=t("ui/core/components/auth/PhoneNumberEntry.kt")
  assertTrue(p.contains("prefixIcon ="));assertTrue(p.contains("ChatyInlineAction("))
  assertTrue(p.contains("readOnly = false"));assertTrue(p.contains("Choose country"));assertTrue(p.contains("Search country or code"))
 }
 @Test fun emailOtpUsesExistingSixCellSanitizerAndResendTimerWithoutSms() {
  val otp=t("ui/auth/OtpVerificationScreen.kt")
  assertTrue(otp.contains("OtpVerificationLogic.sanitizeCode"));assertTrue(otp.contains("OtpVerificationLogic.cooldownSeconds(resendDeadline,"))
  assertTrue(otp.contains("Paste email code"));assertFalse(otp.contains("SmsRetriever"))
  val c=t("ui/core/components/auth/OtpCodeCells.kt")
  assertTrue(c.contains("repeat(OtpVerificationLogic.CODE_LENGTH)"))
  assertTrue(c.contains("auth_otp_field_cd"));assertTrue(c.contains("BasicTextField("))
 }
 @Test fun auditFixesCaptureVerifiedCodeAndSeparateTickFromSuccess() {
  val otp=t("ui/auth/OtpVerificationScreen.kt")
  assertTrue(otp.contains("val submitted = code"));assertTrue(otp.contains("verify(submitted)"))
  assertTrue(otp.contains("onVerified(submitted)"));assertTrue(otp.contains("shouldClearCodeClipboard"))
  assertTrue(otp.contains("ChatyHapticsPort.selection(feedbackView)"))
  val auto=otp.substringAfter("LaunchedEffect(code, isLoading)").substringBefore("fun handleResend")
  assertFalse(auto.contains("ChatyHapticsPort.success"))
 }
 @Test fun backFocusAndOfflineObservationDoNotAddAuthRetries() {
  val support=t("ui/core/components/auth/AuthFormSupport.kt")
  assertTrue(support.contains("BackHandler(enabled = hasDraft)"))
  assertTrue(support.contains("registerDefaultNetworkCallback(callback)"))
  assertTrue(support.contains("unregisterNetworkCallback(callback)"))
  assertFalse(support.contains("requestCode("));assertFalse(support.contains("login("))
  assertTrue(t("ui/auth/AuthComponents.kt").contains("bringIntoView.bringIntoView()"))
  for(n in listOf("LoginScreen.kt", "RegisterScreen.kt")) {
   val screen=t("ui/auth/"+n)
   assertTrue(screen.contains("AuthOfflineNotice(online, theme)"))
   assertTrue(screen.contains("AuthErrorNotice(errorMessage, theme,"))
   assertTrue(screen.contains("rememberAuthBackAction("))
  }
 }
 @Test fun resetOtpIsMemoryOnlyAtTheRouteOwner() {
  val root=t("ui/AppRoot.kt")
  assertTrue(root.contains("var resetCode by remember { mutableStateOf(\"\") }"))
  assertFalse(root.contains("var resetCode by androidx.compose.runtime.saveable.rememberSaveable"))
  assertTrue(root.contains("onPasswordUpdated = { resetCode = \"\"; otpResumePending = false; route = AuthRoute.Home }"))
 }
 @Test fun coveredAuthLayersCancelWorkAndCannotInterceptBack() {
  val root=t("ui/AppRoot.kt")
  assertTrue(root.contains("active = route == AuthRoute.Login"))
  assertTrue(root.contains("active = route == AuthRoute.Register"))
  for(n in listOf("LoginScreen.kt", "RegisterScreen.kt")) {
   val screen=t("ui/auth/"+n)
   assertTrue(screen.contains("rememberAuthRequestScope(active)"))
   assertTrue(screen.contains("rememberAuthBackAction(active &&"))
  }
  val support=t("ui/core/components/auth/AuthFormSupport.kt")
  assertTrue(support.contains("if (confirming && hasDraft)"))
  assertTrue(support.contains("DisposableEffect(scope, active)"))
  val api=t("data/api/ApiBackendService.kt")
  assertTrue(api.substringAfter("private suspend fun adoptCodeOrAddedAccount").substringBefore("val oldHash").contains("currentCoroutineContext().ensureActive()"))
 }
}
