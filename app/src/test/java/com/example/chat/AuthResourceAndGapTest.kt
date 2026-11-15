package com.example.chat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
class AuthResourceAndGapTest {
 private val root=File("app/src/main")
 private fun source(path:String)=File(root,"kotlin/com/example/chat/$path").readText()
 private fun names(locale:String):Set<String> {
  val document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root,"res/$locale/strings_auth.xml"))
  val nodes=document.getElementsByTagName("string")
  return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
 }
 @Test fun allAuthResourceKeysExistInEverySupportedLocale() {
  val english=names("values")
  for(locale in listOf("values-hi","values-ar","values-iw")) assertEquals(locale,english,names(locale))
  val helper=File(root,"java/com/example/chat/core/i18n/AuthCopy.kt").readText()
  Regex(" to \"([a-z0-9_]+)\"").findAll(helper).forEach {assertTrue(it.groupValues[1],it.groupValues[1] in english)}
 }
 @Test fun sharedIndependentTargetsAndDirectLegacyActionsOwnExternalGap() {
  val shared=source("ui/core/components/auth/ChatyAuthComponents.kt")
  val controls=source("ui/auth/AuthComponents.kt")
  assertTrue(shared.contains("val touchGap = 8.dp"))
  assertTrue(controls.contains("padding(vertical=ChatyUiTokens.touchGap / 2).graphicsLayer"))
  assertTrue(controls.contains("Column(modifier = modifier.padding(vertical=ChatyUiTokens.touchGap / 2))"))
  for(file in listOf("OtpVerificationScreen.kt","CreateNewPasswordScreen.kt","ForgotPasswordScreen.kt"))
   assertTrue(file,source("ui/auth/$file").contains("padding(ChatyUiTokens.touchGap / 2)"))
  assertTrue(shared.contains("padding(horizontal=ChatyUiTokens.touchGap / 2).selectable"))
 }
 @Test fun deviceNumberIsReadOnlyAndCaptionDoesNotClaimSelectionBeforePick() {
  val source=source("ui/core/components/auth/PhoneNumberEntry.kt")
  assertTrue(source.contains("readOnly = true"))
  assertTrue(source.contains("if(value.isNotBlank()) Text(authCopy(\"Number selected"))
  assertTrue(source.contains("if(!mounted || !currentEnabled || hintBusy)"))
 }
 @Test fun otpUsesSharedShapeAndLayoutPaddingNeverUsesSevenDpAlias() {
  val otp=source("ui/core/components/auth/OtpCodeCells.kt")
  assertFalse(otp.contains("RoundedCornerShape(ChatyUiTokens.space12)"))
  assertTrue(otp.contains(".background(colors.inputFill, ChatyUiTokens.cardShape)"))
  val auth=File(root,"kotlin/com/example/chat/ui/auth")
  auth.walkTopDown().filter {it.extension=="kt"}.forEach {file->
   assertFalse(file.name,Regex("padding\\([^)]*ChatyUiTokens\\.space7\\b").containsMatchIn(file.readText()))
  }
 }

 @Test fun verifiedInvitationDestinationFlowsIntoBothHostsAndResetsRememberedWorkspace() {
  val app=source("ui/AppRoot.kt")
  assertTrue(app.contains("AuthV2Host(v2Flow,false,v2Workspace,"))
  assertTrue(app.contains("AuthV2Host(v2Flow,true,v2Workspace,"))
  assertFalse(app.contains("AuthV2Host(v2Flow,true,ServiceLocator.apiBackend.config.tenantId"))
  assertTrue(source("ui/auth/v2/AuthV2Host.kt").contains("workspace by remember(initialWorkspace)"))
 }

}
