package community.mitmachim.nativeapp

import org.junit.Assert.*
import org.junit.Test

class ThreadReadingTest {
 @Test fun sideRailOnlyActivatesDuringScrollingOrItsOwnDrag(){
  assertFalse(threadRailActive(false,false))
  assertTrue(threadRailActive(true,false))
  assertTrue(threadRailActive(false,true))
  assertTrue(threadRailActive(true,true))
 }
 @Test fun prependingKeepsTheSamePostAndPixelOffset(){
  assertEquals(ThreadReaderAnchor(3,37),threadAnchorAfterMerge(listOf(4,5),listOf(1,2,3,4,5),4,37))
 }
 @Test fun removingOldPagesKeepsTheVisiblePost(){
  assertEquals(ThreadReaderAnchor(1,92),threadAnchorAfterMerge((1..8).toList(),(4..10).toList(),5,92))
 }
 @Test fun positiveSpaceAboveVisiblePostIsPreserved(){
  assertEquals(ThreadReaderAnchor(2,-48),threadAnchorAfterMerge(listOf(3,4),listOf(1,2,3,4),3,-48))
 }
 @Test fun appendingDoesNotInterruptScrollingWhenIndexDoesNotChange(){
  assertNull(threadAnchorAfterMerge(listOf(1,2),listOf(1,2,3),2,17))
 }
 @Test fun missingAnchorDoesNotInventAScrollDestination(){
  assertNull(threadAnchorAfterMerge(listOf(1,2),listOf(2,3),1,17))
  assertNull(threadAnchorAfterMerge(listOf(1,2),listOf(1,2,3),3,17))
 }
 @Test fun upcomingPageLoadsBeforeTheLastPost(){
  assertEquals(false,threadPrefetchDirection(12,15,20,false,true,false))
  assertNull(threadPrefetchDirection(5,10,20,false,true,false))
 }
 @Test fun previousPageRequiresMovingBackNearTheStart(){
  assertEquals(true,threadPrefetchDirection(3,6,20,true,true,true))
  assertNull(threadPrefetchDirection(3,6,20,true,true,false))
  assertNull(threadPrefetchDirection(10,15,20,true,true,true))
 }
 @Test fun pagingDoesNotFetchBeyondBoundariesOrAnEmptyLayout(){
  assertNull(threadPrefetchDirection(0,5,6,false,false,true))
  assertNull(threadPrefetchDirection(0,5,6,false,false,false))
  assertNull(threadPrefetchDirection(-1,-1,20,true,true,false))
  assertNull(threadPrefetchDirection(0,0,0,true,true,false))
 }
 private fun page(page:Int,pids:List<Int>,tid:Int=42)=ThreadPage(
  Topic(tid,17,"Topic","Category","Writer",100,0,1),pids.map{Post(it,it,"Writer","<p>Body $it</p>",1,false)},page,10)

 @Test fun adjacentPagesPreserveOrderAndDeduplicateOverlappingPosts(){
  val window=ThreadWindow(mapOf(2 to page(2,listOf(4,5,6)))).add(page(3,listOf(6,7,8))).add(page(1,listOf(1,2,3,4)))
  assertEquals((1..8).toList(),window.posts.map{it.id})
  assertEquals(1,window.firstPage);assertEquals(3,window.lastPage)
  assertEquals(3,window.pageOf(8))
 }
 @Test fun nonAdjacentJumpDoesNotPretendMissingPagesWereLoaded(){
  val window=ThreadWindow(mapOf(1 to page(1,listOf(1,2)))).add(page(5,listOf(13,14)))
  assertEquals(setOf(5),window.pages.keys);assertFalse(window.contains(2))
  assertFalse(window.contains(12));assertTrue(window.contains(13))
 }
 @Test fun boundedWindowKeepsTheVisiblePageDuringAppendAndPrepend(){
  val initial=ThreadWindow(mapOf(2 to page(2,listOf(4)),3 to page(3,listOf(7))))
  val appended=initial.add(page(4,listOf(10)),anchorPage=3,maxPages=2)
  assertEquals(setOf(3,4),appended.pages.keys)
  val prepended=initial.add(page(1,listOf(1)),anchorPage=2,maxPages=2)
  assertEquals(setOf(1,2),prepended.pages.keys)
 }
 @Test(expected=IllegalArgumentException::class) fun pagesFromDifferentTopicsCannotBeCombined(){
  ThreadWindow(mapOf(1 to page(1,listOf(1)))).add(page(2,listOf(4),tid=91))
 }
 @Test fun railSelectsAnExactPostIncludingBothBoundaries(){
  assertEquals(1,threadTargetIndex(-1f,100));assertEquals(100,threadTargetIndex(2f,100))
  assertEquals(51,threadTargetIndex(.5f,101));assertEquals(1,threadTargetIndex(.5f,0))
  assertEquals(1f,threadProgress(100,100));assertEquals(0f,threadProgress(1,100))
  assertTrue(threadProgress(40,100)<threadProgress(80,100))
 }
 @Test fun searchMatchesHebrewWithDiacriticsAndAllQueryWords(){
  val post=Post(1,1,"Writer","<p>פִּתְרוֹן פשוט למסך קטן</p>",1,false)
  assertTrue(threadPostMatches(post,"פתרון קטן"));assertFalse(threadPostMatches(post,"פתרון גדול"))
 }
 @Test fun hiddenSpoilersAndDeletedPostsAreNotLeakedInSearchSnippets(){
  val post=Post(1,1,"Writer","<p>Visible</p><details><summary>Open</summary>SECRET</details><blockquote class='spoiler'>SECRET</blockquote>",1,false)
  assertFalse(threadPostMatches(post,"SECRET"));assertFalse(threadSearchSnippet(post,"Visible").contains("SECRET"))
  assertFalse(threadPostMatches(post.copy(deleted=true),"Visible"))
 }
 @Test fun chatGroupingRequiresSameKnownAuthorAndSameDay(){
  val first=Post(2,2,"Writer","Text",1790812800000L,false,uid=42)
  assertTrue(sameChatGroup(first,first.copy(id=3,index=3,time=first.time+60000)))
  assertFalse(sameChatGroup(first,first.copy(uid=0)));assertFalse(sameChatGroup(first,first.copy(toPid=1)))
  assertFalse(sameChatGroup(first,first.copy(time=first.time+6*60000)))
  assertFalse(sameChatGroup(first.copy(index=1),first.copy(index=2)))
 }
 @Test fun partiallySearchedTopicNeverClaimsCompletion(){
  assertFalse(ThreadScan(checkedPages=setOf(1,3,4),totalPages=3).complete)
  assertTrue(ThreadScan(checkedPages=setOf(1,2,3),totalPages=3).complete)
 }
 @Test fun quickReplyDraftsAreSeparatedByUserAndTopic(){
  assertNotEquals(quickReplyDraftKey(42,1),quickReplyDraftKey(43,1))
  assertNotEquals(quickReplyDraftKey(42,1),quickReplyDraftKey(42,2))
 }
 @Test fun shortChatTextRetainsParagraphsAndEnglish(){
  val text=chatPlainText("<p>שלום</p><p>Android 12</p>")!!
  assertTrue(text.contains("שלום"));assertTrue(text.contains("Android 12"));assertTrue(text.contains('\n'))
  assertEquals("שורה ראשונה\nsecond line",chatPlainText("שורה ראשונה<br>second line"))
  assertEquals("שלום\nAndroid 12",chatPlainText("<div><p>שלום</p><p>Android 12</p></div>"))
 }
 @Test fun chatPlainShortcutNeverFlattensSpoilersLinksOrMedia(){
  assertNull(chatPlainText("<p>רגיל</p><details><summary>ספוילר</summary>SECRET</details>"))
  assertNull(chatPlainText("<p>רגיל</p><blockquote class='spoiler'>SECRET</blockquote>"))
  assertNull(chatPlainText("<p><a href='https://mitmachim.top'>קישור</a></p>"))
  assertNull(chatPlainText("<img src='https://mitmachim.top/a.png'>"))
  assertNull(chatPlainText("<pre>val x = 1</pre>"))
 }
 @Test fun topicTeaserExcludesHiddenContent(){
  val topic=topic(org.json.JSONObject().put("tid",42).put("title","דיון").put("teaser",org.json.JSONObject().put("content","<p>מידע גלוי</p><details>SECRET</details><blockquote class='spoiler'>SECRET</blockquote>")))
  assertEquals("מידע גלוי",topic.excerpt)
 }
 @Test fun chatWhitespaceIsCompactWithoutLosingExplicitLineBreaks(){
  assertEquals("שלום עולם\nAndroid 12",chatPlainText("<div>\n  <p> שלום   עולם </p>\n <p> Android&nbsp;12 </p>\n</div>"))
  assertEquals("אחת\n\nשתיים",chatPlainText("אחת<br><br>שתיים"))
 }
 @Test fun ownChatMessagesBelongOnTheLeft(){
  assertFalse(chatBubbleOnRight(mine=true));assertTrue(chatBubbleOnRight(mine=false))
 }
}
