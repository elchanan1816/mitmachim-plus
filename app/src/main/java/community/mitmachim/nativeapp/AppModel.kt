package community.mitmachim.nativeapp

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

object RuntimeSafety { @Volatile var offlineDemo=false }
class CommunityApplication:Application(),ImageLoaderFactory {
 override fun onCreate(){super.onCreate();AppUpdateWorker.scheduleIfEnabled(this)}
 val session by lazy { SessionJar(this) }
 val mediaClient by lazy {
  val guard=okhttp3.Interceptor { chain ->
   if(RuntimeSafety.offlineDemo || !isMediaUrl(chain.request().url)) throw IOException("לא ניתן לטעון את המדיה מכתובת זו")
   chain.proceed(chain.request())
  }
  OkHttpClient.Builder().cookieJar(session).followRedirects(true).followSslRedirects(false).addInterceptor(guard).addNetworkInterceptor(guard).callTimeout(40,java.util.concurrent.TimeUnit.SECONDS).build()
 }
 override fun newImageLoader()=ImageLoader.Builder(this).okHttpClient(mediaClient).crossfade(true).build()
}
data class Route(val kind:String="home",val cid:Int=0,val tid:Int=0,val page:Int=1,val query:String="",val end:Boolean=false,val index:Int=0,val pid:Int=0,val draft:String="",val by:String="",val range:Int=0,val searchIn:String="titlesposts",val scrollOffset:Int=0)
sealed class Load<out T> { data object Waiting:Load<Nothing>();data class Ready<T>(val value:T):Load<T>();data class Failed(val message:String):Load<Nothing>() }
data class SavedDraft(val key:String,val title:String,val content:String,val cid:Int,val tid:Int,val toPid:Int,val updated:Long,val files:JSONArray,val mode:String="editor",val index:Int=0)
class AppModel(app:Application):AndroidViewModel(app) {
 val prefs=app.getSharedPreferences("native-ui",Context.MODE_PRIVATE)
 private val jar=(app as CommunityApplication).session
 val api=ForumApi(jar)
 val demo=RuntimeSafety.offlineDemo
 var route by mutableStateOf(Route());private set
 private val history=mutableListOf<Route>()
 var directory by mutableStateOf<Load<List<Category>>>(Load.Waiting);private set
 var page by mutableStateOf<Load<Page>>(Load.Waiting);private set
 var thread by mutableStateOf<Load<ThreadPage>>(Load.Waiting);private set
 var threadWindow by mutableStateOf<ThreadWindow?>(null);private set
 var threadNavigating by mutableStateOf(false);private set
 var threadPageLoading by mutableIntStateOf(0);private set
 var threadNavigationError by mutableStateOf("");private set
 var threadAdjacentError by mutableStateOf("");private set
 var threadScrollRequest by mutableIntStateOf(0);private set
 var threadReturn by mutableStateOf<Route?>(null);private set
 var threadStyle by mutableStateOf(prefs.getString("threadStyle","discussion")?:"discussion");private set
 var continuousReading by mutableStateOf(prefs.getBoolean("continuousReading",true));private set
 var quickReplyOpen by mutableStateOf(false)
 var threadScan by mutableStateOf(ThreadScan());private set
 var threadVisiblePage by mutableIntStateOf(1);private set
 private var threadVisibleIndex=0
 private var threadVisiblePid=0
 private var threadVisibleOffset=0
 private val threadReturns=mutableListOf<Route>()
 private val threadCache=linkedMapOf<Int,MutableMap<Int,ThreadPage>>()
 private val threadCacheTime=mutableMapOf<Pair<Int,Int>,Long>()
 private var adjacentJob:Job?=null
 private var scanJob:Job?=null
 private var scanGeneration=0
 private var adjacentGeneration=0
 private var threadLoadGeneration=0
 var notices by mutableStateOf<Load<List<Notice>>>(Load.Waiting);private set
 var account by mutableStateOf(Account());private set
 var refreshing by mutableStateOf(false);private set
 var sending by mutableStateOf(false)
 var banner by mutableStateOf("");private set
 var browserRequest by mutableStateOf<String?>(null);private set
 fun consumeBrowserRequest(){browserRequest=null}
 fun openCategory(category:Category){open(categoryShortcut(category))}
 var theme by mutableStateOf(prefs.getString("theme","system")?:"system");private set
 var fontScale by mutableFloatStateOf(prefs.getFloat("fontScale",if(prefs.getBoolean("largeText",false))1.13f else 1f));private set
 var saved by mutableStateOf(readTopics("saved"));private set
 var recent by mutableStateOf(emptyList<Topic>());private set
 var favoriteCategories by mutableStateOf(prefs.getStringSet("favoriteCategories",emptySet()).orEmpty().mapNotNull{it.toIntOrNull()}.toSet());private set
 var draftRevision by mutableIntStateOf(0);private set
 val unreadCount get()=(notices as? Load.Ready)?.value?.count{!it.read}?:0
 private var job:Job?=null
 private var directoryJob:Job?=null
 private var noticesJob:Job?=null
 private val feedCache=linkedMapOf<Route,Page>()
 init {
  if(demo){directory=Load.Ready(demoCategories);loadRoute()}
  else viewModelScope.launch {
   runCatching { api.config();if(api.account.uid>0)api.verifyAccount()else Account() }.onSuccess{account=it}
   recent=readTopics("history-${account.uid}")
   reloadDirectory();loadRoute();refreshNotices()
  }
 }
 fun chooseTheme(value:String){theme=value;prefs.edit().putString("theme",value).apply()}
 fun chooseFontScale(value:Float){fontScale=value.coerceIn(.85f,1.5f);prefs.edit().putFloat("fontScale",fontScale).apply()}
 fun chooseThreadStyle(value:String){if(value in setOf("discussion","chat")){threadStyle=value;prefs.edit().putString("threadStyle",value).apply()}}
 fun chooseContinuousReading(value:Boolean){continuousReading=value;prefs.edit().putBoolean("continuousReading",value).apply()}
 fun showMessage(value:String){banner=value}
 fun clearMessage(){banner=""}
 fun open(requested:Route){
  if(sending){showMessage("השליחה בעיצומה. המתינו לסיומה.");return}
  val value=resolveCategoryShortcut(requested,(directory as? Load.Ready)?.value.orEmpty())
  if(value.kind=="browser"){browserRequest=safeLink(value.query)?.takeIf{isForumUrl(it)&&it.host=="mitmachim.top"}?.toString();return}
  if(value.kind=="post"){openPost(value.pid);return}
  if(value.kind=="topic"&&route.kind=="topic"&&value.tid==route.tid){
   navigateThread(value.page,value.index,value.pid,value.end,rememberReturn=value.index>0||value.pid>0,offset=value.scrollOffset);return
  }
  stopThreadScan();stopAdjacentLoading();quickReplyOpen=false
  if(value.kind=="topic"&&value.tid!=route.tid){threadReturns.clear();threadReturn=null}
  history.add(route);route=when {
  value.kind in listOf("editor","edit")&&value.draft.isEmpty()->value.copy(draft="draft-v7-${account.uid}-${UUID.randomUUID()}")
  value.kind=="topic"&&value.index==0&&!value.end&&value.page==1&&value.pid==0->value.copy(index=lastPosition(value.tid),scrollOffset=lastPositionOffset(value.tid))
  else->value
 };loadRoute()}
 fun root(kind:String){if(sending){showMessage("השליחה בעיצומה. המתינו לסיומה.");return};stopThreadScan();stopAdjacentLoading();quickReplyOpen=false;history.clear();route=Route(kind);loadRoute()}
 fun back():Boolean {
  if(sending){showMessage("השליחה בעיצומה. המתינו לסיומה.");return true}
  stopThreadScan();stopAdjacentLoading();quickReplyOpen=false
  if(history.isNotEmpty()){val previous=history.removeAt(history.lastIndex);route=if(previous.kind=="topic")previous.copy(index=lastPosition(previous.tid),scrollOffset=lastPositionOffset(previous.tid),end=false,pid=0)else previous;loadRoute();return true}
  if(route.kind!="home"){root("home");return true};return false
 }
 fun completePublishing(result:PublishResult){
  history.removeAll{it.kind=="editor"&&it.draft==route.draft}
  if(!result.queued){invalidateThread(result.tid);while(history.lastOrNull()?.let{it.kind=="topic"&&it.tid==result.tid}==true)history.removeAt(history.lastIndex)}
  route=if(result.queued){if(history.isNotEmpty())history.removeAt(history.lastIndex)else Route()}else Route("topic",tid=result.tid,index=result.index,pid=result.pid,end=result.index==0)
  loadRoute()
 }
 fun completeEditing(tid:Int,index:Int,pid:Int){
  history.removeAll{it.kind=="edit"&&it.draft==route.draft}
  invalidateThread(tid);while(history.lastOrNull()?.let{it.kind=="topic"&&it.tid==tid}==true)history.removeAt(history.lastIndex)
  route=Route("topic",tid=tid,index=index,pid=pid)
  loadRoute()
 }
 fun completeQuickReply(result:PublishResult){
  if(result.queued){showMessage("התגובה נשלחה וממתינה לאישור צוות הפורום");return}
  showMessage("התגובה פורסמה")
  invalidateThread(result.tid)
  route=route.copy(page=threadVisiblePage,index=result.index,pid=result.pid,end=result.index==0,scrollOffset=0)
  loadRoute(true)
 }
 val canBack get()=history.isNotEmpty()||route.kind!="home"
 fun retry(){if(route.kind=="topic"&&threadVisibleIndex>0)route=route.copy(page=threadVisiblePage,index=threadVisibleIndex,pid=threadVisiblePid,scrollOffset=threadVisibleOffset,end=false);loadRoute(true);if(directory is Load.Failed)reloadDirectory()}
 fun retryThreadNavigation(){loadRoute()}
 fun markCurrentThreadRead(){val data=(thread as? Load.Ready)?.value?:return;if(demo)return;viewModelScope.launch{try{api.markTopicRead(data.topic.id);prefs.edit().putInt("read-${account.uid}-${data.topic.id}",data.topic.posts).apply();showMessage("הדיון סומן כנקרא בפורום")}catch(e:Exception){showMessage(friendlyError(e))}}}
 fun reloadDirectory(){if(demo)return;directoryJob?.cancel();directoryJob=viewModelScope.launch {directory=result {api.categories()}}}
 private suspend fun <T> result(block:suspend()->T):Load<T> = try{Load.Ready(block())}catch(e:CancellationException){throw e}catch(e:Exception){Load.Failed(friendlyError(e))}
 private fun loadRoute(refresh:Boolean=false){val generation=++threadLoadGeneration;job?.cancel();val r=route;if(r.kind!="topic")threadNavigating=false;job=viewModelScope.launch {
  refreshing=refresh
  try {when(r.kind){
   "home","category","search","popular","unsolved","unread","tag"->{
    if(!refresh)page=feedCache[r]?.let{Load.Ready(it)}?:Load.Waiting
    if(r.kind=="search"&&r.query.isBlank()){page=Load.Ready(Page(emptyList(),1,1,0));return@launch}
    val next=if(demo)Load.Ready(Page(demoTopics,1,3,60,"מחשבים וטכנולוגיה"))else result {when(r.kind){"search"->api.search(r.query,r.page,r.cid,r.by,r.range,r.searchIn);"popular","unsolved","unread","tag"->api.discovery(r.kind,r.page,r.query);else->api.feed(r.cid,r.page)}}
    if(next is Load.Ready){feedCache[r]=next.value;while(feedCache.size>30)feedCache.remove(feedCache.keys.first())}
    if(next is Load.Failed&&page is Load.Ready)showMessage(next.message)else page=next
    if(!demo)account=api.account
   }
   "topic"->loadThread(r,refresh,generation)
   "notifications"->refreshNotices()
  }}finally{if(generation==threadLoadGeneration)refreshing=false}
 }}
 fun readIndex(tid:Int)=prefs.getInt("read-${account.uid}-$tid",0)
 fun lastPosition(tid:Int)=prefs.getInt("position-${account.uid}-$tid",readIndex(tid))
 fun lastPositionOffset(tid:Int)=prefs.getInt("position-offset-${account.uid}-$tid",0)
 fun rememberPosition(tid:Int,index:Int,offset:Int=0){
  if(index<=0)return
  val edit=prefs.edit().putInt("position-${account.uid}-$tid",index).putInt("position-offset-${account.uid}-$tid",offset.coerceAtLeast(0))
  if(index>readIndex(tid))edit.putInt("read-${account.uid}-$tid",index)
  edit.apply()
 }
 fun rememberThreadViewport(post:Post,offset:Int){
  if(route.kind!="topic"||threadNavigating)return
  threadVisibleIndex=post.index;threadVisiblePid=post.id;threadVisibleOffset=offset.coerceAtLeast(0)
  threadVisiblePage=threadWindow?.pageOf(post.id)?:threadVisiblePage
  rememberPosition(route.tid,post.index,threadVisibleOffset)
 }
 private fun cacheThread(data:ThreadPage){
  val pages=threadCache.getOrPut(data.topic.id){linkedMapOf()};pages[data.page]=data
  threadCacheTime[data.topic.id to data.page]=System.currentTimeMillis()
  while(pages.size>20){val old=pages.keys.first();pages.remove(old);threadCacheTime.remove(data.topic.id to old)}
  while(threadCache.size>6){val old=threadCache.keys.first();threadCache.remove(old);threadCacheTime.keys.removeAll{it.first==old}}
 }
 private fun invalidateThread(tid:Int){threadCache.remove(tid);threadCacheTime.keys.removeAll{it.first==tid}}
 private fun freshThreadPage(tid:Int,page:Int)=threadCache[tid]?.get(page)?.takeIf{System.currentTimeMillis()-(threadCacheTime[tid to page]?:0)<60000}
 private fun cachedThread(r:Route):ThreadPage?=threadCache[r.tid]?.values?.firstOrNull{data->
  freshThreadPage(r.tid,data.page)!=null&&when{r.end->data.page==data.pages;r.pid>0->data.posts.any{it.id==r.pid};r.index>0->data.posts.any{it.index==r.index};else->data.page==r.page}
 }
 private fun stopAdjacentLoading(){adjacentGeneration++;adjacentJob?.cancel();adjacentJob=null;threadPageLoading=0}
 private suspend fun loadThread(r:Route,refresh:Boolean,generation:Int){
  stopAdjacentLoading()
  val sameTopic=(thread as? Load.Ready)?.value?.topic?.id==r.tid
  if(!sameTopic){threadScan=ThreadScan();thread=Load.Waiting;threadWindow=null;threadVisibleIndex=0;threadVisiblePid=0;threadVisibleOffset=0;threadVisiblePage=r.page}
  threadNavigating=true;threadNavigationError="";threadAdjacentError=""
  try{
   val cached=if(!demo&&!refresh)cachedThread(r)else null
   val data=cached?:if(demo)demoThread(r)else fetchThread(r)
   if(cached==null)cacheThread(data)
   threadWindow=ThreadWindow(mapOf(data.page to data))
   thread=Load.Ready(data);threadVisiblePage=data.page
   threadScrollRequest++
   recent=(listOf(data.topic)+recent.filter{it.id!=data.topic.id}).take(100);storeTopics("history-${account.uid}",recent)
  }catch(e:CancellationException){throw e}catch(e:Exception){
   if(sameTopic&&thread is Load.Ready)threadNavigationError=friendlyError(e)else thread=Load.Failed(friendlyError(e))
  }finally{if(generation==threadLoadGeneration)threadNavigating=false}
 }
 private suspend fun fetchThread(r:Route):ThreadPage {
  if(r.end){val pages=(thread as? Load.Ready)?.value?.takeIf{it.topic.id==r.tid}?.pages
   if(pages!=null){val last=api.thread(r.tid,pages);return if(last.page!=last.pages)api.thread(r.tid,last.pages)else last}
  }
  val data=api.thread(r.tid,r.page,r.index)
  return if(r.end&&data.page!=data.pages)api.thread(r.tid,data.pages)else data
 }
 fun navigateThread(page:Int=1,index:Int=0,pid:Int=0,end:Boolean=false,rememberReturn:Boolean=false,offset:Int=0){
  if(route.kind!="topic"||sending)return
  if(rememberReturn&&threadVisibleIndex>0){
   threadReturns.add(route.copy(page=threadVisiblePage,index=threadVisibleIndex,pid=threadVisiblePid,end=false,scrollOffset=threadVisibleOffset))
   if(threadReturns.size>8)threadReturns.removeAt(0)
   threadReturn=threadReturns.lastOrNull()
  }
  stopAdjacentLoading();threadAdjacentError=""
  val data=(thread as? Load.Ready)?.value
  val window=threadWindow
  val target=when{end->data?.pages?:page;else->page.coerceAtLeast(1)}
  val local=when{end->window?.pages?.get(target);pid>0->window?.pages?.values?.firstOrNull{d->d.posts.any{it.id==pid}};index>0->window?.pages?.values?.firstOrNull{d->d.posts.any{it.index==index}};else->window?.pages?.get(target)}
  route=route.copy(page=local?.page?:target,index=if(index==0&&!end)local?.posts?.firstOrNull()?.index?:0 else index,pid=pid,end=end,scrollOffset=offset)
  threadNavigationError=""
  if(local!=null&&window!=null){threadLoadGeneration++;job?.cancel();threadNavigating=false;thread=Load.Ready(local.copy(posts=window.posts));threadVisiblePage=local.page;threadScrollRequest++}
  else loadRoute()
 }
 fun returnToThreadPosition(){
  val previous=threadReturns.removeLastOrNull()?:return
  threadReturn=threadReturns.lastOrNull()
  navigateThread(previous.page,previous.index,previous.pid,offset=previous.scrollOffset)
 }
 fun loadAdjacentThreadPage(before:Boolean=false,beforeMerge:((List<Post>)->Unit)?=null){
  val data=(thread as? Load.Ready)?.value?:return
  val window=threadWindow?:return
  if(threadNavigating||threadPageLoading!=0||route.kind!="topic")return
  val next=if(before)window.firstPage-1 else window.lastPage+1
  if(next !in 1..data.pages)return
  threadPageLoading=next;threadAdjacentError=""
  val generation=++adjacentGeneration
  val tid=route.tid;val uid=account.uid
  adjacentJob=viewModelScope.launch{
   try{
    val page=freshThreadPage(tid,next)?:if(demo)demoThread(Route("topic",tid=tid,page=next))else api.thread(tid,next)
    if(generation!=adjacentGeneration||route.kind!="topic"||route.tid!=tid||account.uid!=uid)return@launch
    require(page.page==next){"הפורום לא החזיר את עמוד הדיון המבוקש"}
    cacheThread(page)
    val updated=(threadWindow?:window).add(page,threadVisiblePage)
    beforeMerge?.invoke(updated.posts)
    threadWindow=updated
    thread=Load.Ready(page.copy(posts=updated.posts,page=threadVisiblePage))
   }catch(e:CancellationException){throw e}catch(e:Exception){if(generation==adjacentGeneration)threadAdjacentError=friendlyError(e)}finally{if(generation==adjacentGeneration)threadPageLoading=0}
  }
 }
 fun startThreadScan(query:String,replyTo:Int=0,continueScan:Boolean=false){
  val data=(thread as? Load.Ready)?.value?:return
  if(query.trim().isEmpty()&&replyTo<=0)return
  scanJob?.cancel()
  val generation=++scanGeneration
  val tid=data.topic.id;val uid=account.uid
  val initial=if(continueScan&&threadScan.query==query.trim()&&threadScan.replyTo==replyTo)threadScan else ThreadScan(query.trim(),replyTo,totalPages=data.pages)
  threadScan=initial.copy(running=true,error="")
  scanJob=viewModelScope.launch{
   try{
    var checked=initial.checkedPages
    var found=initial.posts
    var total=data.pages
    val available=threadCache[tid].orEmpty().values.sortedBy{it.page}
    fun collect(page:ThreadPage){
     checked=checked+page.page;total=page.pages
     found=(found+page.posts.filter{!it.deleted&&if(replyTo>0)it.toPid==replyTo else threadPostMatches(it,query)}).distinctBy{it.id}.sortedBy{it.index}
     threadScan=ThreadScan(query.trim(),replyTo,found,checked,total,running=true)
    }
    available.filter{it.page !in checked}.forEach(::collect)
    var fetched=0
    for(page in 1..total){
     ensureActive()
     if(page in checked)continue
     if(fetched>=20)break
     val loaded=if(demo)demoThread(Route("topic",tid=tid,page=page))else api.thread(tid,page)
     if(route.kind!="topic"||route.tid!=tid||account.uid!=uid)return@launch
     require(loaded.page==page){"הפורום לא החזיר את עמוד הדיון המבוקש"}
     cacheThread(loaded);collect(loaded);fetched++
     delay(180)
    }
   }catch(e:CancellationException){throw e}catch(e:Exception){if(generation==scanGeneration)threadScan=threadScan.copy(error=friendlyError(e))}finally{if(generation==scanGeneration)threadScan=threadScan.copy(running=false)}
  }
 }
 fun stopThreadScan(){scanGeneration++;scanJob?.cancel();scanJob=null;threadScan=threadScan.copy(running=false)}
 fun refreshNotices(){noticesJob?.cancel();val uid=account.uid;if(uid<=0){notices=Load.Ready(emptyList());return};noticesJob=viewModelScope.launch {val loaded=result{api.notifications()};if(account.uid==uid)notices=loaded}}
 fun readNotice(notice:Notice){if(notice.read||notice.id.isBlank())return;viewModelScope.launch {try{api.markNoticeRead(notice.id);val list=(notices as? Load.Ready)?.value?:return@launch;notices=Load.Ready(list.map{if(it.id==notice.id)it.copy(read=true)else it})}catch(e:Exception){showMessage(friendlyError(e))}}}
 suspend fun login(name:String,password:String):String? {
  if(demo)return "התחברות אינה זמינה בבדיקות"
  return try{
   val pending=history.lastOrNull()?.takeIf{it.kind in listOf("editor","edit","topic")}
   account=api.login(name,password);feedCache.clear();recent=readTopics("history-${account.uid}");reloadDirectory();refreshNotices()
   if(pending!=null){var draft=pending.draft;if(pending.kind=="topic"){val old=quickReplyDraftKey(0,pending.tid);val next=quickReplyDraftKey(account.uid,pending.tid);prefs.getString(old,null)?.let{prefs.edit().putString(next,it).remove(old).apply()}}
    if(draft.startsWith("draft-v7-0-")){val next=draft.replaceFirst("draft-v7-0-","draft-v7-${account.uid}-");prefs.getString(draft,null)?.let{prefs.edit().putString(next,it).remove(draft).apply()};draft=next};history.clear();threadCache.clear();threadCacheTime.clear();threadWindow=null;thread=Load.Waiting;route=pending.copy(draft=draft);loadRoute()}else root("profile")
   null
  }catch(e:CancellationException){throw e}catch(e:Exception){friendlyError(e)}
 }
 suspend fun refreshAccount():String? = try{account=api.verifyAccount();refreshNotices();null}catch(e:CancellationException){throw e}catch(e:Exception){account=api.account;friendlyError(e)}
 fun logout(){job?.cancel();directoryJob?.cancel();noticesJob?.cancel();adjacentJob?.cancel();stopThreadScan();feedCache.clear();threadCache.clear();threadCacheTime.clear();threadWindow=null;threadReturns.clear();threadReturn=null;ForumNotificationWorker.disable(getApplication());api.forget();account=Account();notices=Load.Ready(emptyList());thread=Load.Waiting;directory=Load.Waiting;recent=readTopics("history-0");reloadDirectory();root("home")}
 fun openPost(pid:Int){
  val local=threadWindow?.posts?.firstOrNull{it.id==pid}
  if(route.kind=="topic"&&local!=null){navigateThread(index=local.index,pid=pid,rememberReturn=true);return}
  viewModelScope.launch{try{open(api.postRoute(pid))}catch(e:Exception){showMessage(friendlyError(e))}}
 }
 fun toggleSaved(t:Topic){saved=if(saved.any{it.id==t.id})saved.filterNot{it.id==t.id}else listOf(t)+saved;storeTopics("saved",saved)}
 fun toggleFavorite(cid:Int){favoriteCategories=if(cid in favoriteCategories)favoriteCategories-cid else favoriteCategories+cid;prefs.edit().putStringSet("favoriteCategories",favoriteCategories.map{it.toString()}.toSet()).apply()}
 private fun readTopics(key:String)=runCatching{JSONArray(prefs.getString(key,"[]")).objects().map(::topic)}.getOrDefault(emptyList())
 private fun storeTopics(key:String,items:List<Topic>){val a=JSONArray();items.forEach{a.put(JSONObject().put("tid",it.id).put("cid",it.cid).put("title",it.title).put("postcount",it.posts).put("viewcount",it.views).put("timestamp",it.time).put("category",JSONObject().put("name",it.category)).put("user",JSONObject().put("username",it.author).put("uid",it.authorUid).put("userslug",it.authorSlug).put("picture",it.avatar)))};prefs.edit().putString(key,a.toString()).apply()}
 fun saveDraft(key:String,value:JSONObject){prefs.edit().putString(key,value.put("updated",System.currentTimeMillis()).toString()).apply();draftRevision++}
 fun deleteAttachment(uri:android.net.Uri){if(uri.scheme!="file")return;runCatching{val directory=java.io.File(getApplication<Application>().filesDir,"draft-attachments").canonicalFile;val file=java.io.File(uri.path?:return).canonicalFile;if(file.parentFile==directory&&file.isFile)file.delete()}}
 fun deleteDraft(key:String){runCatching{restoreFiles(JSONObject(prefs.getString(key,"{}")?:"{}").array("files")).forEach{deleteAttachment(it.uri)}};prefs.edit().remove(key).apply();draftRevision++}
 fun drafts():List<SavedDraft> = prefs.all.filterKeys{it.startsWith("draft-v7-${account.uid}-")||it.startsWith("draft-")&&!it.startsWith("draft-v7-")}.mapNotNull{(key,v)->runCatching{val d=JSONObject(v as String);SavedDraft(key,d.optString("title"),d.optString("content"),d.optInt("cid"),d.optInt("tid",key.split('-').getOrNull(1)?.toIntOrNull()?:0),d.optInt("toPid"),d.optLong("updated"),d.array("files"),d.optString("mode","editor"),d.optInt("index"))}.getOrNull()}.filter{it.title.isNotBlank()||it.content.isNotBlank()||it.files.length()>0}.sortedByDescending{it.updated}
 companion object {
  val demoCategories=listOf(Category(17,0,"מחשבים וטכנולוגיה",26410),Category(280,0,"בינה מלאכותית",1342))
  val demoTopics=listOf(Topic(95167,280,"מה חדש בעולם הבינה המלאכותית?","בינה מלאכותית","משתמש לדוגמה",28,412,1,true),Topic(106,17,"טיפים קטנים שעושים סדר גדול במחשב","מחשבים וטכנולוגיה","חבר קהילה",12,286,1))
 }
}
