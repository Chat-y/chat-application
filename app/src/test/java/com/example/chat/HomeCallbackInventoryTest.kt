package com.example.chat
import java.io.File
import org.junit.Test
import org.junit.Assert.*
/** Source-text checks only. Not compiled, not run. Keeps the callback inventory honest against the files it names. */
class HomeCallbackInventoryTest {
 private fun src(p:String)=File("app/src/main/$p").readText()
 @Test fun namesAreUnique() {
  assertEquals(HomeCallbackInventory.items.size,HomeCallbackInventory.items.map {it.name}.toSet().size)
 }
 @Test fun everyDeclarationExistsInItsFile() {
  for(c in HomeCallbackInventory.items) assertTrue("${c.name} not declared in ${c.file}",src(c.file).contains(c.declaration))
 }
 @Test fun everyOptionalInputHasADefaultSoExistingCallersCompile() {
  for(c in HomeCallbackInventory.items) assertTrue("${c.name} lacks default",c.declaration.contains("=")||c.declaration.contains("?"))
 }
 @Test fun missingNamesStillAbsentFromAllMainSources() {
  val all=File("app/src/main").walkTopDown().filter {it.extension=="kt"}.joinToString("\n") {it.readText()}
  for(n in HomeCallbackInventory.missing) assertFalse("$n now exists: move it into the inventory",all.contains(n))
 }
 @Test fun unwiredClaimsMatchSource() {
  val screen=src(HomeCallbackInventory.SCREEN); val host=src(HomeCallbackInventory.HOST)
  for(c in HomeCallbackInventory.byWiring(HomeWiring.DECLARED_NOT_WIRED).filter {it.file==HomeCallbackInventory.CHIPS})
   assertFalse("${c.name} is passed from Screen now: update inventory",screen.contains("${c.name} ="))
  for(c in HomeCallbackInventory.byWiring(HomeWiring.DEFAULT_ONLY)) assertFalse("${c.name} is passed from Host now: update inventory",host.contains("${c.name} ="))
 }
 @Test fun wiredClaimsAreCalledFromScreen() {
  val screen=src(HomeCallbackInventory.SCREEN)
  for(c in HomeCallbackInventory.byWiring(HomeWiring.WIRED)) assertTrue("${c.name} not passed in Screen",screen.contains("${c.name} =")||screen.contains("${c.name}="))
 }
}
