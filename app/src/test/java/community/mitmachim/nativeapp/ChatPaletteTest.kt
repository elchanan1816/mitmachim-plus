package community.mitmachim.nativeapp

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class ChatPaletteTest {
 private fun contrast(a:Color,b:Color):Float{val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)}
 @Test fun chatOptInNeverChangesTheNormalScheme(){
  val normal=lightColorScheme(primary=Color(0xFF00838F))
  assertSame(normal,chatAppearanceScheme(normal,false,false))
  assertSame(normal,chatAppearanceScheme(normal,false,true))
  assertNotEquals(normal.primary,chatAppearanceScheme(normal,true,false).primary)
 }
 @Test fun ownMessagesAreGreenAndSeparateFromOtherMessagesInBothThemes(){
  for(dark in listOf(false,true)){
   val own=chatMessageColor(true,dark);val other=chatMessageColor(false,dark)
   assertTrue(own.green>own.red);assertTrue(own.green>own.blue);assertNotEquals(own,other)
   assertTrue(contrast(chatColorScheme(dark).onSurface,own)>=4.5f)
   assertTrue(contrast(chatColorScheme(dark).onSurface,other)>=4.5f)
  }
 }
 @Test fun controlsAndMetadataRemainReadableOnBothBubbleColors(){
  for(dark in listOf(false,true)){
   val scheme=chatColorScheme(dark)
   assertTrue(contrast(scheme.primary,scheme.onPrimary)>=4.5f)
   for(mine in listOf(false,true)){
    assertTrue(contrast(scheme.primary,chatMessageColor(mine,dark))>=4.5f)
    assertTrue(contrast(scheme.onSurfaceVariant,chatMessageColor(mine,dark))>=4.5f)
   }
  }
 }
 @Test fun wallpaperIsOpaqueAndDoodlesStaySubtle(){
  for(dark in listOf(false,true)){assertEquals(1f,chatPaperColor(dark).alpha,.001f);assertTrue(chatDoodleColor(dark).alpha in .15f.. .55f)}
 }
 @Test fun replyAreaUsesPermissionsRatherThanChatAppearance(){
  assertEquals(ThreadReplyAvailability.Compose,threadReplyAvailability(false,true,42))
  assertEquals(ThreadReplyAvailability.Compose,threadReplyAvailability(false,true,0))
  assertEquals(ThreadReplyAvailability.Locked,threadReplyAvailability(true,true,42))
  assertEquals(ThreadReplyAvailability.Locked,threadReplyAvailability(true,false,0))
  assertEquals(ThreadReplyAvailability.Login,threadReplyAvailability(false,false,0))
  assertEquals(ThreadReplyAvailability.Restricted,threadReplyAvailability(false,false,42))
 }
 @Test fun railFitsEvenWhenKeyboardLeavesLittleReadingHeight(){
  for(height in listOf(0f,24f,80f,128f,300f,600f)){
   val reserved=if(threadRailButtonsVisible(height))72f else 0f
   assertTrue(threadRailHeight(height)+reserved<=height)
   assertTrue(threadRailHeight(height) in 0f..180f)
  }
  assertFalse(threadRailButtonsVisible(80f));assertTrue(threadRailButtonsVisible(128f))
 }
}
