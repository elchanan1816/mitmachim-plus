package community.mitmachim.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryAppearanceTest {
 @Test fun categorySymbolsMatchTheirSubject(){
  assertEquals(CategorySymbol.COMPUTER,categorySymbol("מחשבים וטכנולוגיה"))
  assertEquals(CategorySymbol.PHONE,categorySymbol("Android - אנדרואיד"))
  assertEquals(CategorySymbol.NETWORK,categorySymbol("רשתות"))
  assertEquals(CategorySymbol.AUDIO,categorySymbol("נגנים ותוכנותיהם"))
  assertEquals(CategorySymbol.IMAGE,categorySymbol("צילום"))
  assertEquals(CategorySymbol.PRINTER,categorySymbol("מדפסות וסורקים"))
  assertEquals(CategorySymbol.CODE,categorySymbol("תכנות ופיתוח"))
  assertEquals(CategorySymbol.BOOK,categorySymbol("מדריכים"))
  assertEquals(CategorySymbol.FOLDER,categorySymbol("תחום חדש ללא שם מוכר"))
 }
 @Test fun englishCategoryLabelsAreCaseInsensitive(){
  assertEquals(CategorySymbol.COMPUTER,categorySymbol("LINUX"))
  assertEquals(CategorySymbol.CODE,categorySymbol("SOFTWARE"))
  assertEquals(CategorySymbol.SHIELD,categorySymbol("Security"))
 }
}
