package community.mitmachim.nativeapp

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

/** Used only when chat appearance is enabled; normal forum appearance stays unchanged. */
fun chatColorScheme(dark:Boolean):ColorScheme=if(dark)darkColorScheme(
 primary=Color(0xFF70DBB7),onPrimary=Color(0xFF00382C),primaryContainer=Color(0xFF005C4B),onPrimaryContainer=Color(0xFFE2FFF1),
 secondary=Color(0xFFAFD3C6),onSecondary=Color(0xFF163B30),secondaryContainer=Color(0xFF294D40),onSecondaryContainer=Color(0xFFD6F5E5),
 tertiary=Color(0xFFC4D5AE),onTertiary=Color(0xFF283A1C),tertiaryContainer=Color(0xFF405234),onTertiaryContainer=Color(0xFFE0F1CA),
 background=Color(0xFF0B141A),onBackground=Color(0xFFE9EDEF),surface=Color(0xFF202C33),onSurface=Color(0xFFE9EDEF),
 surfaceVariant=Color(0xFF293940),onSurfaceVariant=Color(0xFFB7C8C2),outline=Color(0xFF80948B),outlineVariant=Color(0xFF3C5048),
 surfaceContainerLowest=Color(0xFF0B141A),surfaceContainerLow=Color(0xFF172229),surfaceContainer=Color(0xFF25343B),surfaceContainerHigh=Color(0xFF2D3C43),surfaceContainerHighest=Color(0xFF35454C),
 inverseSurface=Color(0xFFE9EDEF),inverseOnSurface=Color(0xFF172229),inversePrimary=Color(0xFF006B57)
)else lightColorScheme(
 primary=Color(0xFF006B57),onPrimary=Color.White,primaryContainer=Color(0xFFD9F4DB),onPrimaryContainer=Color(0xFF123D2B),
 secondary=Color(0xFF46665A),onSecondary=Color.White,secondaryContainer=Color(0xFFE0EFE6),onSecondaryContainer=Color(0xFF223D32),
 tertiary=Color(0xFF536441),onTertiary=Color.White,tertiaryContainer=Color(0xFFE1EDCE),onTertiaryContainer=Color(0xFF2B3B1D),
 background=Color(0xFFF0F2F5),onBackground=Color(0xFF17232B),surface=Color.White,onSurface=Color(0xFF17232B),
 surfaceVariant=Color(0xFFE9EFEB),onSurfaceVariant=Color(0xFF53675D),outline=Color(0xFF73867B),outlineVariant=Color(0xFFD6DFD9),
 surfaceContainerLowest=Color.White,surfaceContainerLow=Color(0xFFF6F8F6),surfaceContainer=Color(0xFFEEF3EF),surfaceContainerHigh=Color(0xFFE7EEE8),surfaceContainerHighest=Color(0xFFDFE8E0),
 inverseSurface=Color(0xFF25343B),inverseOnSurface=Color(0xFFF0F4F1),inversePrimary=Color(0xFF70DBB7)
)
fun chatAppearanceScheme(normal:ColorScheme,chat:Boolean,dark:Boolean)=if(chat)chatColorScheme(dark)else normal
fun chatMessageColor(mine:Boolean,dark:Boolean)=when{
 dark&&mine->Color(0xFF005C4B)
 dark->Color(0xFF202C33)
 mine->Color(0xFFD9FDD3)
 else->Color.White
}
fun chatPaperColor(dark:Boolean)=if(dark)Color(0xFF0B141A)else Color(0xFFF4F1EB)
fun chatDoodleColor(dark:Boolean)=if(dark)Color(0xFF75877D).copy(alpha=.24f)else Color(0xFFBFB5A3).copy(alpha=.48f)
