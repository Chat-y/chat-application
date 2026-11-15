package com.example.chat
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class AuthCompletionSourceTest {
 private val root get() = if(File("app/src/main").exists()) File("app/src/main") else File("src/main")
 @Test fun allAuthVectorInputsResolveThroughChatyRoles() {
  val roles=File(root,"kotlin/com/example/chat/ui/icons/ChatyMaterialIconRoles.kt").readText()
  File(root,"kotlin/com/example/chat/ui/auth").walkTopDown().filter {it.extension=="kt"}.forEach {file->
   val source=file.readText()
   Regex("Icons\\.(?:Rounded|AutoMirrored\\.Rounded)\\.(\\w+)").findAll(source).forEach {
    assertTrue("${file.name}: ${it.groupValues[1]}",roles.contains("\"${it.groupValues[1]}\" to"))
   }
  }
 }
 @Test fun firebaseDeferredButFrameworkPushServiceRetained() {
  val manifest=File(root,"AndroidManifest.xml").readText()
  assertTrue(manifest.contains("FirebaseInitProvider"));assertTrue(manifest.contains("tools:node=\"remove\""))
  assertTrue(manifest.contains(".notifications.ChatyFirebaseMessagingService"))
  val locator=File(root,"kotlin/com/example/chat/ServiceLocator.kt").readText()
  assertTrue(locator.contains("check(apiBackend.isAuthenticated)"));assertTrue(locator.contains("FirebaseApp.initializeApp"))
 }
 @Test fun authScreensUseTokensForExplicitFontSizes() {
  File(root,"kotlin/com/example/chat/ui/auth").walkTopDown().filter {it.extension=="kt"}.forEach {
   assertFalse(it.name,Regex("fontSize\\s*=\\s*[0-9]+(?:\\.[0-9]+)?\\.sp").containsMatchIn(it.readText()))
  }
 }
}
