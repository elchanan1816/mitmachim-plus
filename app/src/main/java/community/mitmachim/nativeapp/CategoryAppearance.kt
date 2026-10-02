package community.mitmachim.nativeapp

import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

enum class CategorySymbol { COMPUTER, PHONE, HARDWARE, CODE, NETWORK, AUDIO, VIDEO, IMAGE, BOOK, SHIELD, HELP, DOWNLOAD, ANNOUNCEMENT, PRINTER, FOLDER }

/** Semantic, stable symbols: list position and search ordering never change a category's icon. */
fun categorySymbol(name:String):CategorySymbol {
 val label=name.lowercase(Locale.ROOT)
 fun has(vararg words:String)=words.any{label.contains(it)}
 return when {
  has("מדפס", "printer", "הדפס") -> CategorySymbol.PRINTER
  has("רשת", "אינטרנט", "תקשורת", "wifi", "wi-fi", "נטפרי", "סינון") -> CategorySymbol.NETWORK
  has("מוזיק", "מוסיק", "שמע", "אודיו", "נגנים", "נגן", "music", "audio") -> CategorySymbol.AUDIO
  has("וידאו", "סרט", "video") -> CategorySymbol.VIDEO
  has("צילום", "תמונ", "גרפיק", "עיצוב", "photoshop", "פוטושופ") -> CategorySymbol.IMAGE
  has("אבטח", "וירוס", "security", "הגנה") -> CategorySymbol.SHIELD
  has("אנדרואיד", "android", "טלפון", "סלול", "סמארטפון", "פלאפון", "iphone", "ios") -> CategorySymbol.PHONE
  has("חומרה", "אלקטרונ", "מעגל", "hardware", "רכיב") -> CategorySymbol.HARDWARE
  has("תכנות", "פיתוח", "קוד", "תוכנות", "תוכנה", "program", "software") -> CategorySymbol.CODE
  has("מדריכ", "תורה", "ספר", "לימוד", "guide") -> CategorySymbol.BOOK
  has("הורד", "download", "קבצים") -> CategorySymbol.DOWNLOAD
  has("מחשב", "טכנולוג", "windows", "ווינדוס", "וינדוס", "linux", "לינוקס", "macos", "מקינטוש", "מערכות הפעלה") -> CategorySymbol.COMPUTER
  has("עזרה", "תמיכה", "שאל", "בקשות", "help", "support", "תיקון") -> CategorySymbol.HELP
  has("הודעות", "עדכונ", "חדשות", "הכרז", "ניהול", "פורום") -> CategorySymbol.ANNOUNCEMENT
  else -> CategorySymbol.FOLDER
 }
}

fun categoryIcon(name:String):ImageVector=when(categorySymbol(name)) {
 CategorySymbol.COMPUTER->AppIcons.Computer
 CategorySymbol.PHONE->AppIcons.PhoneAndroid
 CategorySymbol.HARDWARE->AppIcons.Memory
 CategorySymbol.CODE->AppIcons.Code
 CategorySymbol.NETWORK->AppIcons.Network
 CategorySymbol.AUDIO->AppIcons.Music
 CategorySymbol.VIDEO->AppIcons.PlayCircleOutline
 CategorySymbol.IMAGE->AppIcons.Image
 CategorySymbol.BOOK->AppIcons.Book
 CategorySymbol.SHIELD->AppIcons.Shield
 CategorySymbol.HELP->AppIcons.Help
 CategorySymbol.DOWNLOAD->AppIcons.Download
 CategorySymbol.ANNOUNCEMENT->AppIcons.Announcement
 CategorySymbol.PRINTER->AppIcons.Printer
 CategorySymbol.FOLDER->AppIcons.Folder
}
