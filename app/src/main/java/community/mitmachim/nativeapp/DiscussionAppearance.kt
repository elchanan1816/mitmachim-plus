package community.mitmachim.nativeapp

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.sp

val LocalDiscussionSans=staticCompositionLocalOf{false}
val DiscussionTypography=ChatTypography.copy(bodyLarge=ChatTypography.bodyLarge.copy(fontSize=17.sp,lineHeight=24.sp))
fun discussionColorScheme(original:ColorScheme,chat:Boolean):ColorScheme {
 if(chat)return original
 val dark=original.background.luminance()<.3f
 return original.copy(
  surfaceTint=original.primary,
  primaryContainer=if(dark)Color(0xFF214C52)else Color(0xFFD7F1F2),
  onPrimaryContainer=if(dark)Color(0xFFD8F5F6)else Color(0xFF003F46),
  secondaryContainer=if(dark)Color(0xFF263F61)else Color(0xFFDDEBF8),
  onSecondaryContainer=if(dark)Color(0xFFDAE7FF)else Color(0xFF174576)
 )
}
fun discussionPostColor(scheme:ColorScheme,index:Int)=if(index==1)lerp(scheme.surface,scheme.primary,if(scheme.background.luminance()<.3f).12f else .08f)else scheme.surface
fun richTextUsesSystemFont(compact:Boolean,discussionSans:Boolean)=compact||discussionSans
fun richTextLineSpacing(compact:Boolean,discussionSans:Boolean)=if(compact)1.10f else if(discussionSans)1.25f else 1.35f
