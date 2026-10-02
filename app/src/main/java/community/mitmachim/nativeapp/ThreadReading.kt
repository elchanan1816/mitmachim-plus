package community.mitmachim.nativeapp

import org.jsoup.Jsoup
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** A bounded, contiguous reading window. Stable post IDs keep the viewport anchored when pages join. */
data class ThreadWindow(val pages:Map<Int,ThreadPage>) {
 val firstPage get()=pages.keys.minOrNull()?:1
 val lastPage get()=pages.keys.maxOrNull()?:1
 val posts get()=pages.toSortedMap().values.flatMap{it.posts}.distinctBy{it.id}.sortedBy{it.index}
 fun pageOf(pid:Int):Int?=pages.entries.firstOrNull{entry->entry.value.posts.any{it.id==pid}}?.key
 fun contains(index:Int)=posts.any{it.index==index}
 fun add(data:ThreadPage,anchorPage:Int=data.page,maxPages:Int=10):ThreadWindow {
  require(maxPages>0)
  if(pages.isNotEmpty())require(pages.values.first().topic.id==data.topic.id)
  val next=pages.toMutableMap().apply{put(data.page,data)}
  // Non-adjacent jumps deliberately start a fresh window, without claiming intervening posts are loaded.
  if(data.page<firstPage-1||data.page>lastPage+1)return ThreadWindow(mapOf(data.page to data))
  while(next.size>maxPages){
   val first=next.keys.minOrNull()!!;val last=next.keys.maxOrNull()!!
   val firstDistance=anchorPage-first;val lastDistance=last-anchorPage
   // On a tie retain the incoming edge, otherwise a full window could keep loading the same page.
   next.remove(if(firstDistance>lastDistance||firstDistance==lastDistance&&data.page>=anchorPage)first else last)
  }
  return ThreadWindow(next.toSortedMap())
 }
}

fun threadTargetIndex(progress:Float,total:Int):Int=(1+(total-1).coerceAtLeast(0)*progress.coerceIn(0f,1f)).roundToInt().coerceIn(1,total.coerceAtLeast(1))
fun threadProgress(index:Int,total:Int):Float=((index-1).toFloat()/(total-1).coerceAtLeast(1)).coerceIn(0f,1f)
fun threadNavigationLabel(page:Int,pages:Int)="עמוד $page מתוך $pages"
fun quickReplyDraftKey(uid:Int,tid:Int)="draft-v7-$uid-quick-$tid"
fun threadImageFrameHeight(widthDp:Float)=(widthDp*.75f).coerceIn(72f,360f)
fun threadRailButtonsVisible(heightDp:Float)=heightDp>=128f
fun threadRailActive(scrolling:Boolean,dragging:Boolean)=scrolling||dragging
fun threadRailHeight(heightDp:Float)=(heightDp-if(threadRailButtonsVisible(heightDp))72f else 0f).coerceIn(0f,180f)

data class ThreadReaderAnchor(val postIndex:Int,val scrollOffset:Int)
fun threadAnchorAfterMerge(oldIds:List<Int>,newIds:List<Int>,pid:Int,offset:Int):ThreadReaderAnchor? {
 val old=oldIds.indexOf(pid);val next=newIds.indexOf(pid)
 return if(old>=0&&next>=0&&old!=next)ThreadReaderAnchor(next,offset)else null
}
/** null means no fetch, true means previous, false means next. Never fetch just for empty layout. */
fun threadPrefetchDirection(firstVisible:Int,lastVisible:Int,count:Int,canPrevious:Boolean,canNext:Boolean,movingBack:Boolean):Boolean? {
 if(count<=0||firstVisible<0||lastVisible<firstVisible)return null
 val margin=minOf(4,(count/3).coerceAtLeast(1))
 return when{
  movingBack&&canPrevious&&firstVisible<=margin->true
  !movingBack&&canNext&&lastVisible>=count-1-margin->false
  else->null
 }
}
data class ThreadPrefetchViewport(val item:Int,val offset:Int,val first:Int,val last:Int,val scrolling:Boolean,val loading:Boolean)

/** Search never exposes a spoiler's hidden body in its result snippets. */
fun threadSearchText(post:Post):String {
 if(post.deleted)return ""
 val doc=Jsoup.parseBodyFragment(post.html)
 doc.select("script,style,details,blockquote.spoiler").remove()
 return doc.text()
}
fun normalizedSearchText(value:String):String=Normalizer.normalize(value,Normalizer.Form.NFD)
 .replace(Regex("[\\u0591-\\u05BD\\u05BF-\\u05C7]"),"").lowercase(Locale.ROOT).replace(Regex("\\s+")," ").trim()
fun threadPostMatches(post:Post,query:String):Boolean {
 val words=normalizedSearchText(query).split(' ').filter{it.isNotBlank()}
 val text=normalizedSearchText(threadSearchText(post))
 return words.isNotEmpty()&&words.all(text::contains)
}
fun threadSearchSnippet(post:Post,query:String):String {
 val text=threadSearchText(post)
 val first=query.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
 val offset=text.indexOf(first,ignoreCase=true).coerceAtLeast(0)
 val start=(offset-45).coerceAtLeast(0);val end=(start+220).coerceAtMost(text.length)
 return (if(start>0)"…"else "")+text.substring(start,end)+(if(end<text.length)"…"else "")
}
fun sameChatGroup(previous:Post?,post:Post):Boolean=previous!=null&&previous.index>1&&post.index>1&&
 previous.uid>0&&previous.uid==post.uid&&post.time>=previous.time&&post.time-previous.time<=5*60*1000&&
 post.toPid==0&&!previous.deleted&&!post.deleted&&chatDay(previous.time)==chatDay(post.time)
fun chatDay(time:Long):String=if(time>0)SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(Date(time))else ""
fun chatTime(time:Long):String=if(time>0)SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(time))else ""

data class ThreadScan(val query:String="",val replyTo:Int=0,val posts:List<Post> = emptyList(),
 val checkedPages:Set<Int> = emptySet(),val totalPages:Int=1,val running:Boolean=false,val error:String="") {
 val complete get()=(1..totalPages).all{it in checkedPages}
}

/** Debug-only local data: enough pages and reply relationships to verify navigation without live writes. */
fun demoThread(route:Route):ThreadPage {
 val page=when{route.end->3;route.index>0->(route.index-1)/6+1;else->route.page}.coerceIn(1,3)
 val topic=AppModel.demoTopics.first().copy(id=route.tid,posts=18)
 val posts=((page-1)*6+1..page*6).map { index->
  val mine=index%4 in setOf(0,1)
  Post(index,index,if(mine)"החשבון שלי"else "חבר קהילה",
   when(index){1->"<p>ברוכים הבאים לדיון. כאן אפשר לקרוא ברצף, להגיב ולמצוא תשובות.</p>"
    3->"<p>מצאתי פתרון שעובד גם במסך קטן.</p><blockquote class='spoiler'><button>הצגת ספוילר</button><p>תוכן מוסתר</p></blockquote>"
    5->"<p>דוגמת קוד:</p><pre>val message = &quot;שלום&quot;</pre>"
    else->"<p>תגובה מספר $index לדיון. זהו תוכן בדיקה מקומי לקריאה ולחיפוש — אפשר להמשיך לעמוד הבא.</p><p>עוד מידע שימושי על השאלה ועל הפתרון, עם טקסט מספיק לבדיקת גלילה נוחה.</p>"},
   1790812800000L+index*60000,false,uid=if(mine)999 else 42,toPid=if(index in setOf(4,9,15))3 else 0,online=!mine)
 }
 return ThreadPage(topic,posts,page,3)
}
