package community.mitmachim.nativeapp

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class NativeSmokeTest {
 @get:Rule val compose=createEmptyComposeRule()
 private lateinit var scenario:ActivityScenario<MainActivity>
 @Before fun launch(){val context=InstrumentationRegistry.getInstrumentation().targetContext;context.getSharedPreferences("native-ui",0).edit().clear().commit();scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("local_demo",true));compose.waitForIdle()}
 @After fun close(){scenario.close()}
 @Test fun categoriesAndRotationRemainUsable(){
  assertTrue(RuntimeSafety.offlineDemo)
  compose.onAllNodesWithText("קטגוריות").onFirst().performClick()
  compose.onNodeWithText("איזה תחום מעניין אותך?").assertIsDisplayed()
  compose.onNodeWithText("מחשבים וטכנולוגיה",substring=true).performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("טיפים קטנים שעושים סדר גדול במחשב").fetchSemanticsNodes().isNotEmpty()}
  scenario.recreate();compose.waitForIdle();assertTrue(RuntimeSafety.offlineDemo)
 }
 @Test fun editorHasDirectSendAndAutosaves(){
  compose.onNodeWithContentDescription("כתיבת דיון").performClick()
  compose.onNodeWithText("כותרת הדיון").performTextInput("טיוטת בדיקה שלא נשלחת")
  compose.onNodeWithText("תוכן הפוסט").performTextInput("תוכן בדיקה מקומי בלבד")
  compose.onNodeWithText("פרסום נושא").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("שליחת דמה").assertDoesNotExist()
  compose.onNodeWithContentDescription("חזרה").performClick()
  compose.onNodeWithText("חשבון").performClick()
  compose.onNodeWithText("טיוטות",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("טיוטת בדיקה שלא נשלחת").assertIsDisplayed()
 }
 @Test fun homeHeaderScrollsAway(){
  compose.onNodeWithText("מתמחים טופ\nקהילת ידע וטכנולוגיה").assertIsDisplayed()
  compose.onNodeWithTag("home-feed").performTouchInput{swipeUp()}
  compose.onNodeWithText("מתמחים טופ\nקהילת ידע וטכנולוגיה").assertIsNotDisplayed()
 }
 @Test fun aboutContainsExactDeveloperCredits(){
  compose.onNodeWithText("חשבון").performClick()
  compose.onNodeWithText("אודות",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("@רב יהודה פרחים").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("ובסיוע @הבריסקער רב").performScrollTo().assertIsDisplayed()
 }
 private fun openThread(){
  compose.onNodeWithText("מה חדש בעולם הבינה המלאכותית?").performScrollTo().performClick()
  compose.waitUntil(10000){compose.onAllNodesWithTag("thread-position").fetchSemanticsNodes().isNotEmpty()}
 }
 private fun waitForPage(page:Int){
  compose.waitUntil(10000){compose.onAllNodesWithTag("discussion-post-${(page-1)*6+1}").fetchSemanticsNodes().isNotEmpty()}
 }
 @Test fun ordinaryThreadToolbarHasRoomForItsTitle(){openThread();val bounds=compose.onNodeWithTag("thread-toolbar").getUnclippedBoundsInRoot();org.junit.Assert.assertTrue(bounds.bottom.value-bounds.top.value>=76f)}
 private fun openNavigation(){compose.onNodeWithTag("thread-position").performClick()}
 private fun enableChat(){
  compose.onNodeWithText("חשבון").performClick()
  compose.onNodeWithText("הגדרות",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithContentDescription("הפעלת תצוגת צ׳אט").performClick()
  compose.onNodeWithText("בית").performClick()
 }
 @Test fun pageNavigationDoesNotFillTheBackStack(){
  openThread()
  openNavigation()
  compose.onNodeWithText("הבא",substring=true).performClick()
  waitForPage(2)
  compose.onNodeWithContentDescription("חזרה").performClick()
  compose.onNodeWithTag("home-feed").assertIsDisplayed()
 }
 @Test fun boundariesAndReturnToPriorPlaceRemainAccessible(){
  openThread()
  openNavigation()
  compose.onNodeWithText("תחילת הדיון").assertIsDisplayed()
  compose.onNodeWithText("תגובה אחרונה").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithTag("discussion-post-18").fetchSemanticsNodes().isNotEmpty()}
  openNavigation()
  compose.onNodeWithText("תחילת הדיון").performClick()
  waitForPage(1)
  compose.onNodeWithContentDescription("חזרה למקום הקודם",substring=true).performClick()
  compose.waitUntil(10000){compose.onAllNodesWithTag("discussion-post-18").fetchSemanticsNodes().isNotEmpty()}
 }
 @Test fun discussionIsDefaultAndChatOptInSurvivesRecreation(){
  openThread()
  compose.onNodeWithTag("discussion-post-1").performScrollTo().assertIsDisplayed()
  compose.onNodeWithContentDescription("מעבר לתצוגת צ׳אט").assertDoesNotExist()
  compose.onNodeWithContentDescription("חזרה").performClick()
  enableChat()
  compose.onNodeWithTag("chat-topic-95167").assertIsDisplayed()
  openThread()
  compose.onNodeWithTag("chat-post-1").assertIsDisplayed()
  scenario.recreate();compose.waitForIdle()
  // Routes are not persisted across Activity recreation; the reading preference is.
  if(compose.onAllNodesWithTag("home-feed").fetchSemanticsNodes().isNotEmpty())openThread()
  compose.onNodeWithTag("chat-post-1").assertIsDisplayed()
 }
 @Test fun quickDraftSurvivesMinimizingAndFullEditorHandoff(){
  openThread()
  compose.onNodeWithContentDescription("כתיבת תגובה").performClick()
  compose.onNodeWithTag("quick-reply-input").performTextInput("טיוטה מקומית לתגובה מהירה")
  compose.onNodeWithContentDescription("אפשרויות הכתיבה").performClick()
  compose.onNodeWithText("מזעור אזור הכתיבה").performClick()
  compose.onNodeWithContentDescription("כתיבת תגובה").performClick()
  compose.onNodeWithTag("quick-reply-input").assertTextContains("טיוטה מקומית לתגובה מהירה")
  compose.onNodeWithContentDescription("פתיחת העורך המלא וצירוף קבצים").performClick()
  compose.onNodeWithText("תוכן הפוסט").assertTextContains("טיוטה מקומית לתגובה מהירה")
  compose.onNodeWithText("תוכן הפוסט").performTextInput(" ועוד מהעורך")
  compose.onNodeWithContentDescription("חזרה").performClick()
  compose.onNodeWithContentDescription("כתיבת תגובה").performClick()
  compose.onNodeWithTag("quick-reply-input").assertTextContains("ועוד מהעורך",substring=true)
  compose.onNodeWithContentDescription("פתיחת העורך המלא וצירוף קבצים").performClick()
  compose.onNodeWithText("תוכן הפוסט").assertTextContains("ועוד מהעורך",substring=true)
 }
 @Test fun topicSearchFindsPostAndCanReturnFromItsContext(){
  openThread()
  compose.onNodeWithContentDescription("פעולות הדיון").performClick()
  compose.onNodeWithText("חיפוש בתוך הדיון").performClick()
  compose.onNodeWithText("מילים לחיפוש").performTextInput("מסך קטן")
  compose.onNodeWithText("חיפוש",useUnmergedTree=true).performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("החיפוש הושלם",substring=true).fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("מצאתי פתרון שעובד גם במסך קטן.").performClick()
  compose.onNodeWithTag("discussion-post-3").assertIsDisplayed()
  compose.onNodeWithContentDescription("חזרה למקום הקודם",substring=true).assertIsDisplayed()
 }
 @Test fun normalReaderRestoresItsVisualHierarchyAndKeepsMostOfTheScreen(){
  openThread()
  val screen=compose.onNodeWithTag("thread-screen").fetchSemanticsNode().boundsInRoot
  val reading=compose.onNodeWithTag("thread-posts").fetchSemanticsNode().boundsInRoot
  assertTrue(reading.height>screen.height*.7f)
  compose.onNodeWithText("חשבון").assertDoesNotExist()
  compose.onNodeWithTag("discussion-heading").assertIsDisplayed()
  compose.onNodeWithTag("discussion-tools").performScrollTo().assertIsDisplayed()
  compose.onNodeWithTag("discussion-actions-1").performScrollTo().assertIsDisplayed()
  compose.onNodeWithContentDescription("תגובה לפוסט #1").assertIsDisplayed()
  compose.onNodeWithContentDescription("פעולות לפוסט #1").performClick()
  compose.onNodeWithText("דיסלייק").assertIsDisplayed()
  compose.onNodeWithText("תגובה לפוסט").assertIsDisplayed()
 }
 @Test fun rtlChatBubblesHaveOppositeSidesAndDoNotFillTheScreen(){
  enableChat();openThread()
  val screen=compose.onNodeWithTag("thread-posts").fetchSemanticsNode().boundsInRoot
  val own=compose.onNodeWithTag("chat-post-1").fetchSemanticsNode().boundsInRoot
  val other=compose.onNodeWithTag("chat-post-2").fetchSemanticsNode().boundsInRoot
  val ownRow=compose.onNodeWithTag("chat-row-1").fetchSemanticsNode().boundsInRoot
  val otherRow=compose.onNodeWithTag("chat-row-2").fetchSemanticsNode().boundsInRoot
  assertTrue(ownRow.center.x<screen.center.x)
  assertTrue(otherRow.center.x>screen.center.x)
  assertTrue(compose.onNodeWithTag("chat-avatar-1").fetchSemanticsNode().boundsInRoot.left>=own.right)
  assertTrue(compose.onNodeWithTag("chat-avatar-2").fetchSemanticsNode().boundsInRoot.left>=other.right)
  assertTrue(own.width<screen.width*.9f)
  val avatar=compose.onNodeWithTag("chat-avatar-1").fetchSemanticsNode().boundsInRoot
  assertTrue(kotlin.math.abs(avatar.top-own.top)<2f)
  val wallpaper=compose.onNodeWithTag("chat-discussion-wallpaper").fetchSemanticsNode().boundsInRoot
  val toolbar=compose.onNodeWithTag("thread-toolbar").fetchSemanticsNode().boundsInRoot
  assertTrue(wallpaper.top>=toolbar.bottom)
  compose.onNodeWithTag("discussion-heading").assertDoesNotExist()
  compose.onNodeWithTag("discussion-actions-1").assertDoesNotExist()
 }
 @Test fun homeInvitationEnablesChatWithoutLeavingHome(){
  compose.onNodeWithTag("chat-invitation").performScrollTo().assertIsDisplayed()
  compose.onNodeWithTag("try-chat").performClick()
  compose.onNodeWithTag("chat-invitation").assertDoesNotExist()
  compose.onNodeWithTag("chat-topic-95167").assertIsDisplayed()
  scenario.recreate();compose.waitForIdle()
  compose.onNodeWithTag("chat-topic-95167").assertIsDisplayed()
  compose.onNodeWithText("חשבון").performClick()
  compose.onNodeWithText("הגדרות",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithContentDescription("הפעלת תצוגת צ׳אט").assertIsOn().performClick()
  compose.onNodeWithText("בית").performClick()
  compose.onNodeWithTag("chat-invitation").performScrollTo().assertIsDisplayed()
 }
 @Test fun swipeChoosesReplyWithoutQuotingOrSending(){
  enableChat();openThread()
  compose.onNodeWithTag("chat-row-1").performTouchInput{swipeRight()}
  compose.onNodeWithTag("quick-reply-input").assertIsDisplayed().assertTextEquals("")
  compose.onNodeWithText("בתגובה ל־החשבון שלי").assertIsDisplayed()
  compose.onNodeWithContentDescription("שליחת תגובה").assertIsNotEnabled()
 }
 @Test fun quickPreviewKeepsTheDraftAndRendersSpoilers(){
  openThread();compose.onNodeWithContentDescription("כתיבת תגובה").performClick()
  val draft="**טקסט בדיקה**\n>! תוכן מוסתר"
  compose.onNodeWithTag("quick-reply-input").performTextInput(draft)
  compose.onNodeWithContentDescription("אפשרויות הכתיבה").performClick()
  compose.onNodeWithText("תצוגה לפני שליחה").performClick()
  compose.onNodeWithTag("post-preview").assertIsDisplayed()
  compose.onNodeWithText("הצגת ספוילר").assertIsDisplayed()
  compose.onNodeWithText("חזרה לכתיבה").performScrollTo().performClick()
  compose.onNodeWithTag("quick-reply-input").assertTextContains(draft)
 }
 @Test fun tappingTheTitleShowsTopicDetails(){
  openThread();compose.onNodeWithTag("thread-title").performClick()
  compose.onNodeWithText("18 פוסטים ·",substring=true).assertIsDisplayed()
  compose.onNodeWithText("סגירה").performClick()
  compose.onNodeWithTag("discussion-post-1").performScrollTo().assertIsDisplayed()
 }
 @Test fun ordinaryReplyPromptIsAlwaysVisibleAndReturnsAfterMinimizing(){
  openThread()
  compose.onNodeWithTag("quick-reply-open").assertIsDisplayed().performClick()
  compose.onNodeWithTag("quick-reply-input").performTextInput("טיוטה שנשארת זמינה")
  compose.onNodeWithContentDescription("אפשרויות הכתיבה").performClick()
  compose.onNodeWithText("מזעור אזור הכתיבה").performClick()
  compose.onNodeWithTag("quick-reply-open").assertIsDisplayed().performClick()
  compose.onNodeWithTag("quick-reply-input").assertTextContains("טיוטה שנשארת זמינה")
 }
 @Test fun ordinaryReaderHasNoPermanentSideGutterAndKeepsEndNavigation(){
  openThread()
  compose.waitUntil(4000){compose.onAllNodesWithTag("thread-side-navigation").fetchSemanticsNodes().isEmpty()}
  compose.onNodeWithTag("thread-side-navigation").assertDoesNotExist()
  val screen=compose.onNodeWithTag("thread-screen").fetchSemanticsNode().boundsInRoot
  val posts=compose.onNodeWithTag("thread-posts").fetchSemanticsNode().boundsInRoot
  assertTrue(kotlin.math.abs(screen.width-posts.width)<2f)
  openNavigation()
  compose.onNodeWithText("תגובה אחרונה").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithTag("discussion-post-18").fetchSemanticsNodes().isNotEmpty()}
 }
}
