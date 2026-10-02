package community.mitmachim.nativeapp

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class ChatInteractionTest {
 @Test fun avatarColorsAreStableAcrossCaseAndRenamesWhenUidIsKnown(){
  assertEquals(avatarColorIndex("Alice"),avatarColorIndex(" ALICE "))
  assertEquals(avatarColorIndex("שם קודם",42),avatarColorIndex("שם חדש",42))
  for(uid in 1..100)assertTrue(avatarColorIndex("משתמש",uid) in avatarSwatches.indices)
  assertTrue((1..100).map{avatarColorIndex("משתמש",it)}.toSet().size>1)
 }
 @Test fun initialsSkipMentionPunctuation(){
  assertEquals("ר",avatarInitial("@רב יהודה פרחים"));assertEquals("?",avatarInitial("  "))
 }
 @Test fun avatarSwatchesHaveReadableWhiteLetters(){
  fun channel(value:Long):Double{val s=value/255.0;return if(s<=.04045)s/12.92 else ((s+.055)/1.055).pow(2.4)}
  avatarSwatches.forEach{color->val luminance=.2126*channel((color shr 16)and 255)+.7152*channel((color shr 8)and 255)+.0722*channel(color and 255);assertTrue(1.05/(luminance+.05)>=4.5)}
 }
 @Test fun swipeRequiresDeliberateDistanceAndAcceptsEitherDirection(){
  assertFalse(replySwipeAccepted(63f));assertFalse(replySwipeAccepted(0f))
  assertFalse(replySwipeAccepted(Float.NaN));assertFalse(replySwipeAccepted(Float.POSITIVE_INFINITY))
  assertTrue(replySwipeAccepted(64f));assertTrue(replySwipeAccepted(-64f))
 }
 @Test fun lockedDeletedAndSendingPostsCannotStartSwipeReplies(){
  assertTrue(canSwipeReply(true,false,false))
  assertFalse(canSwipeReply(false,false,false));assertFalse(canSwipeReply(true,true,false));assertFalse(canSwipeReply(true,false,true))
 }
 @Test fun imageFramesAreBoundedAndIndependentOfLoadStatus(){
  assertEquals(72f,threadImageFrameHeight(50f),.01f)
  assertEquals(225f,threadImageFrameHeight(300f),.01f)
  assertEquals(360f,threadImageFrameHeight(960f),.01f)
 }
 @Test fun localPreviewKeepsMarkdownLinksAndHiddenSpoilers(){
  val source="**בדיקה**\n\n[קישור](https://mitmachim.top)\n\n>! SECRET"
  val html=markdownToHtml(source)
  assertTrue(html.contains("<strong>בדיקה</strong>"));assertTrue(html.contains("https://mitmachim.top"))
  assertTrue(html.contains("<details>"));assertFalse(threadSearchText(Post(1,1,"א",html,1,false)).contains("SECRET"))
  assertFalse(markdownToHtml("<script>alert(1)</script>").contains("<script>"))
 }
}
