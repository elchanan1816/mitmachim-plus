package community.mitmachim.nativeapp

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Test

class DiscussionAppearanceTest {
 private fun contrast(a:Color,b:Color):Float=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)
 @Test fun normalContainersAreBrandedAndReadableInBothThemes(){
  for(original in listOf(lightColorScheme(),darkColorScheme())){
   val scheme=discussionColorScheme(original,false)
   assertTrue(scheme.primaryContainer.green>scheme.primaryContainer.red)
   assertTrue(scheme.secondaryContainer.blue>scheme.secondaryContainer.red)
   assertTrue(contrast(scheme.primaryContainer,scheme.onPrimaryContainer)>=4.5f)
   assertTrue(contrast(scheme.secondaryContainer,scheme.onSecondaryContainer)>=4.5f)
   assertEquals(original.primary,scheme.primary);assertEquals(original.background,scheme.background)
  }
 }
 @Test fun ordinaryRestorationNeverChangesChatColors(){
  for(dark in listOf(false,true)){val chat=chatColorScheme(dark);assertSame(chat,discussionColorScheme(chat,true))}
 }
 @Test fun onlyOpeningPostHasAnAccentTint(){
  val scheme=lightColorScheme(primary=Color(0xFF00838F))
  assertNotEquals(scheme.surface,discussionPostColor(scheme,1))
  assertEquals(scheme.surface,discussionPostColor(scheme,2))
 }
 @Test fun systemFontIsScopedToDiscussionsAndChat(){
  assertFalse(richTextUsesSystemFont(false,false))
  assertTrue(richTextUsesSystemFont(false,true));assertTrue(richTextUsesSystemFont(true,false))
  assertEquals(1.10f,richTextLineSpacing(true,true),.001f)
  assertEquals(1.25f,richTextLineSpacing(false,true),.001f)
  assertEquals(1.35f,richTextLineSpacing(false,false),.001f)
  assertEquals(FontFamily.SansSerif,DiscussionTypography.bodyLarge.fontFamily)
  assertEquals(17.sp,DiscussionTypography.bodyLarge.fontSize)
 }
}
