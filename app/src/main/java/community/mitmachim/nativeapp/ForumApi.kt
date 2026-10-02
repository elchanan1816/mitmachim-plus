package community.mitmachim.nativeapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

const val FORUM = "https://mitmachim.top"
class ForumHttpException(val status:Int,message:String):IOException(message)
const val LOGIN_PATH="/api/v3/utilities/login"
fun loginFailure(status:Int,raw:String):String = serverError(status, raw, login = true)
fun plain(value:String)=Jsoup.parse(value).text()
fun JSONArray.objects():List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.array(key:String)=optJSONArray(key)?:JSONArray()
fun JSONObject.obj(key:String)=optJSONObject(key)?:JSONObject()
data class Category(val id:Int,val parent:Int,val name:String,val count:Int,val children:List<Category> = emptyList(),val link:String="")
data class Topic(val id:Int,val cid:Int,val title:String,val category:String,val author:String,val posts:Int,val views:Int,val time:Long,val pinned:Boolean=false,val authorUid:Int=0,val authorSlug:String="",val avatar:String="",val locked:Boolean=false,val solved:Boolean=false,val online:Boolean=false,val excerpt:String="")
data class Post(val id:Int,val index:Int,val author:String,val html:String,val time:Long,val deleted:Boolean,val votes:Int=0,val voted:Boolean=false,val uid:Int=0,val downvoted:Boolean=false,val authorSlug:String="",val avatar:String="",val canEdit:Boolean=false,val canDelete:Boolean=false,val toPid:Int=0,val bookmarked:Boolean=false,val online:Boolean=false)
data class PublicProfile(val uid:Int,val name:String,val slug:String,val avatar:String,val about:String,val joined:Long,val reputation:Int,val posts:Int,val topics:Int,val group:String,val views:Int=0,val lastOnline:Long=0,val followers:Int=0,val following:Int=0,val groups:List<String> = emptyList(),val shares:Int=0,val isFollowing:Boolean=false,val online:Boolean=false)
data class UserPost(val pid:Int,val tid:Int,val title:String,val excerpt:String,val time:Long,val votes:Int)
data class ProfileSection(val topics:List<Topic> = emptyList(),val posts:List<UserPost> = emptyList(),val people:List<Account> = emptyList(),val groups:List<String> = emptyList(),val page:Int=1,val pages:Int=1)
data class ForumTag(val name:String,val count:Int)
data class Page(val topics:List<Topic>,val page:Int,val pages:Int,val total:Int,val name:String="",val children:List<Category> = emptyList(),val link:String="",val watched:Boolean=false)
data class ThreadPage(val topic:Topic,val posts:List<Post>,val page:Int,val pages:Int,val following:Boolean=false,val locked:Boolean=false,val canReply:Boolean=true)
data class Account(val uid:Int=0,val name:String="אורח",val slug:String="",val avatar:String="",val online:Boolean=false)
data class Notice(val text:String,val path:String,val time:Long,val id:String="",val read:Boolean=false)
fun JSONObject.flag(key:String)=optBoolean(key)||optInt(key)==1
fun authenticatedAccount(d:JSONObject):Account {val uid=d.optInt("uid");require(uid>0){"לא נמצא חשבון מחובר בפורום"};return Account(uid,plain(d.optString("displayname",d.optString("username","חבר קהילה"))),d.optString("userslug"),avatarUrl(d.optString("picture")),d.optString("status")=="online")}
fun parseNotices(d:JSONObject):List<Notice> {val rows=d.array("unread").objects()+d.array("read").objects()+d.array("notifications").objects();return rows.map { Notice(plain(it.optString("bodyShort",it.optString("bodyLong"))),it.optString("path"),it.optLong("datetime",it.optLong("timestamp")),it.optString("nid"),it.flag("read")) }.distinctBy { it.id.ifBlank {it.path+it.time+it.text} }.sortedByDescending {it.time}}
fun category(d:JSONObject):Category=Category(d.optInt("cid"),d.optInt("parentCid"),plain(d.optString("name")),d.optInt("totalTopicCount",d.optInt("topic_count")),d.array("children").objects().map(::category),d.optString("link"))
fun avatarUrl(value:String):String=value.takeIf{it.isNotBlank()}?.let(::safeLink)?.toString().orEmpty()
fun topic(d:JSONObject):Topic {
 val user=d.obj("user")
 val preview=org.jsoup.Jsoup.parseBodyFragment(d.obj("teaser").optString("content"))
 preview.select("script,style,details,blockquote.spoiler").remove()
 return Topic(d.optInt("tid"),d.optInt("cid",d.obj("category").optInt("cid")),plain(d.optString("titleRaw",d.optString("title"))),plain(d.obj("category").optString("name")),plain(user.optString("displayname",user.optString("username","חבר קהילה"))),d.optInt("postcount"),d.optInt("viewcount"),d.optLong("lastposttime",d.optLong("timestamp")),d.flag("pinned"),user.optInt("uid"),user.optString("userslug"),avatarUrl(user.optString("picture")),d.flag("locked"),d.flag("isSolved"),user.optString("status")=="online",preview.text().take(180))
}
fun post(d:JSONObject):Post {val user=d.obj("user");return Post(d.optInt("pid"),d.optInt("index")+1,plain(user.optString("displayname",user.optString("username","חבר קהילה"))),d.optString("content"),d.optLong("timestamp"),d.flag("deleted"),d.optInt("votes",d.optInt("upvotes")-d.optInt("downvotes")),d.flag("upvoted"),d.optInt("uid",user.optInt("uid")),d.flag("downvoted"),user.optString("userslug"),avatarUrl(user.optString("picture")),d.flag("display_edit_tools"),d.flag("display_delete_tools"),d.optInt("toPid"),d.flag("bookmarked"),user.optString("status")=="online")}
fun publicProfile(d:JSONObject):PublicProfile {val counts=d.obj("counts");return PublicProfile(d.optInt("uid"),plain(d.optString("displayname",d.optString("username","חבר קהילה"))),d.optString("userslug"),avatarUrl(d.optString("picture")),plain(d.optString("aboutmeParsed",d.optString("aboutme"))),d.optLong("joindate"),d.optInt("reputation"),counts.optInt("posts",d.optInt("postcount")),counts.optInt("topics",d.optInt("topiccount")),plain(d.array("groupTitleArray").optString(0)),d.optInt("profileviews"),d.optLong("lastonline"),counts.optInt("followers",d.optInt("followerCount")),counts.optInt("following",d.optInt("followingCount")),d.array("groups").objects().map{plain(it.optString("name"))}.filter{it.isNotBlank()},counts.optInt("shares"),d.flag("isFollowing"),d.optString("status")=="online")}
fun flatten(items:List<Category>):List<Category> = items.flatMap { listOf(it)+flatten(it.children) }.distinctBy { it.id }
fun safeLink(value:String):HttpUrl? { val u=runCatching { FORUM.toHttpUrl().resolve(value) }.getOrNull();return u?.takeIf { it.scheme=="https" && it.username.isEmpty() && it.password.isEmpty() } }

/** Session cookies only: encrypted at rest. Passwords are never retained. */
interface ForumSession:CookieJar {val requestGate:Mutex;fun clear()}
fun restoreCookie(data:JSONObject):Cookie? {val origin=runCatching{data.optString("origin",FORUM).toHttpUrl()}.getOrNull()?:return null;if(!isForumUrl(origin))return null;return Cookie.parse(origin,data.optString("cookie"))?.takeIf{isForumHost(it.domain)}}
class SessionJar(context:Context):ForumSession {
 override val requestGate=Mutex()
 private val prefs=context.getSharedPreferences("native-session",Context.MODE_PRIVATE)
 private val cookies=mutableListOf<Cookie>()
 private fun key():SecretKey {
  val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) };val alias="mitmachim-native-session-v1"
  (ks.getKey(alias,null) as? SecretKey)?.let { return it }
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
 }
 init { runCatching { val saved=prefs.getString("sealed",null)?:return@runCatching;val parts=saved.split(':');val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));val raw=String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)));JSONArray(raw).objects().forEach { d->restoreCookie(d)?.let(cookies::add) } }.onFailure { prefs.edit().remove("sealed").apply() } }
 @Synchronized override fun loadForRequest(url:HttpUrl):List<Cookie> { cookies.removeAll { it.expiresAt<System.currentTimeMillis() };return if(isForumUrl(url))cookies.filter { it.matches(url) } else emptyList() }
 @Synchronized override fun saveFromResponse(url:HttpUrl,cookies:List<Cookie>) { if(!isForumUrl(url))return;cookies.filter { isForumHost(it.domain) }.forEach { c->this.cookies.removeAll { it.name==c.name && it.domain==c.domain && it.path==c.path };this.cookies.add(c) };persist() }
 private fun persist(){runCatching { val data=JSONArray();cookies.forEach { data.put(JSONObject().put("cookie",it.toString()).put("origin","https://"+it.domain)) };val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());val encrypted=c.doFinal(data.toString().toByteArray());prefs.edit().putString("sealed",Base64.encodeToString(c.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply() }.onFailure { prefs.edit().remove("sealed").apply() } }
 @Synchronized override fun clear(){cookies.clear();prefs.edit().clear().apply()}
}

/** Every HTTP hop must stay on the forum. External media uses a separate, cookieless client. */
class ForumOriginGuard:Interceptor {
 override fun intercept(chain:Interceptor.Chain):Response {
  if(RuntimeSafety.offlineDemo)throw IOException("כל תקשורת הרשת חסומה במצב דמה")
  val u=chain.request().url
  if(!isForumUrl(u))throw IOException("כתובת השירות אינה שייכת לפורום")
  return chain.proceed(chain.request())
 }
}
class ForumApi internal constructor(private val jar:ForumSession,transportForTests:Interceptor?=null) {
 private val client=OkHttpClient.Builder().cookieJar(jar).addInterceptor(ForumOriginGuard()).apply{if(transportForTests!=null)addInterceptor(transportForTests)}.addNetworkInterceptor(ForumOriginGuard()).followRedirects(true).followSslRedirects(false).retryOnConnectionFailure(false).connectTimeout(10,TimeUnit.SECONDS).readTimeout(18,TimeUnit.SECONDS).writeTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()
 private val readClient=client.newBuilder().retryOnConnectionFailure(true).build()
 @Volatile private var csrf=""
 @Volatile var account=Account();private set
 var minTitle=10;private set
 var minPost=2;private set
 var maxPost=327670;private set
 var maxFileBytes=40L*1024*1024;private set
 var postsPerPage=20;private set
 private suspend fun request(path:String,method:String="GET",body:JSONObject?=null):Any =
  if(method=="GET")readWithRetry(path) else jar.requestGate.withLock {
   configUnlocked();verifyAccountUnlocked();requestUnlocked(path,method,body)
  }
 suspend fun updateTopicPage(page:Int):JSONObject {
  require(page>0)
  return request("/api/topic/${AppUpdates.TOPIC_ID}?page=$page&_update=${System.currentTimeMillis()}") as JSONObject
 }
 private suspend fun readWithRetry(path:String):Any {
  try { return requestUnlocked(path) }
  catch(e:IOException) {
   if(!retryableReadFailure(e))throw e
   delay(450)
   return requestUnlocked(path)
  }
 }
 private suspend fun requestUnlocked(path:String,method:String="GET",body:JSONObject?=null):Any=withContext(Dispatchers.IO) {
  require(path.startsWith('/')&&!path.startsWith("//"))
  val req=Request.Builder().url(FORUM+path).header("Accept","application/json").header("Origin",FORUM).header("X-Requested-With","XMLHttpRequest").header("User-Agent","MitmachimNative/7.0 Android")
  if(method!="GET"){check(csrf.isNotEmpty()){ "יש לרענן את ההתחברות לפני הפעולה" };val requestBody=if(method=="DELETE"&&body==null)null else (body?:JSONObject()).toString().toRequestBody("application/json".toMediaType());req.header("x-csrf-token",csrf).method(method,requestBody)}
  (if(method=="GET")readClient else client).newCall(req.build()).awaitResponse().use { r->
   if(!r.isSuccessful){val rawError=r.body?.string().orEmpty();throw ForumHttpException(r.code,serverError(r.code,rawError,path==LOGIN_PATH))}
   if(r.code==204||r.body?.contentLength()==0L)return@withContext JSONObject()
   val raw=r.body?.string()?:throw IOException("הפורום החזיר תשובה ריקה")
   if(!r.header("Content-Type","").orEmpty().contains("json"))throw IOException("הפורום החזיר עמוד שאינו נתונים. נסו שוב מאוחר יותר")
   JSONTokener(raw).nextValue()
  }
 }
 suspend fun config():Account=jar.requestGate.withLock{configUnlocked()}
 private suspend fun configUnlocked():Account {val d=readWithRetry("/api/config") as JSONObject;csrf=d.optString("csrf_token");minTitle=d.optInt("minimumTitleLength",10);minPost=d.optInt("minimumPostLength",2);maxPost=d.optInt("maximumPostLength",327670);maxFileBytes=(d.optLong("maximumFileSize",40000)*1024).coerceIn(1024,40L*1024*1024);postsPerPage=d.optInt("postsPerPage",20).coerceAtLeast(1);account=if(d.flag("loggedIn")&&d.optInt("uid")>0)Account(d.optInt("uid"),if(account.uid==d.optInt("uid"))account.name else "חבר קהילה",account.slug)else Account();return account}
 suspend fun verifyAccount():Account=jar.requestGate.withLock{verifyAccountUnlocked()}
 private suspend fun verifyAccountUnlocked():Account {try{val me=readWithRetry("/api/self") as JSONObject;account=authenticatedAccount(me);return account}catch(e:ForumHttpException){if(e.status==401||e.status==403)account=Account();throw e}}
 suspend fun login(name:String,password:String):Account=jar.requestGate.withLock {
  configUnlocked()
  requestUnlocked(LOGIN_PATH,"POST",JSONObject().put("username",name.trim()).put("password",password).put("remember","on"))
  configUnlocked()
  try{verifyAccountUnlocked()}catch(e:ForumHttpException){throw ForumHttpException(e.status,"בקשת הכניסה הסתיימה, אך אימות החשבון נכשל (HTTP ${e.status}). נסו שוב או בדקו שהחשבון נפתח עם אותם פרטים באתר.")}
 }
 fun forget(){client.dispatcher.cancelAll();jar.clear();csrf="";account=Account()}
 suspend fun categories():List<Category> {val result=mutableListOf<Category>();var p=1;do{val d=request("/api/categories?page=$p") as JSONObject;result.addAll(d.array("categories").objects().map(::category));val pages=d.obj("pagination").optInt("pageCount",1);p++}while(p<=pages);return result.distinctBy { it.id }.map { if(it.children.isNotEmpty())it.copy(children=children(it.id))else it } }
 private suspend fun children(cid:Int):List<Category> {val result=mutableListOf<Category>();val seen=mutableSetOf<Int>();var start=0;while(true){val d=(request("/api/v3/categories/$cid/children?start=$start") as JSONObject).obj("response").array("categories").objects().map(::category);val fresh=d.filter { seen.add(it.id) };if(fresh.isEmpty())break;result.addAll(fresh);start+=d.size};return result}
 suspend fun feed(cid:Int=0,page:Int=1):Page {
  require(cid>=0&&page>0);var raw=request(if(cid>0)"/api/category/$cid?page=$page" else "/api/recent?term=alltime&page=$page")
  if(raw is String){val link=safeLink(raw);val target=link?.takeIf { it.host=="mitmachim.top" }?.encodedPath?.let { Regex("^/category/(\\d+).*").matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() };if(target!=null&&target!=cid)raw=request("/api/category/$target?page=$page") else return Page(emptyList(),1,1,0,link=link?.toString().orEmpty())}
  val d=raw as? JSONObject?:throw IOException("לא ניתן לקרוא את הקטגוריה");val u=d.obj("loggedInUser");if(account.uid>0&&u.optInt("uid")==account.uid)account=account.copy(name=plain(u.optString("username",account.name)))
  return Page(d.array("topics").objects().map(::topic),d.obj("pagination").optInt("currentPage",page),d.obj("pagination").optInt("pageCount",1).coerceAtLeast(1),d.optInt("topic_count",d.optInt("topicCount")),plain(d.optString("name")),if(d.optBoolean("hasMoreSubCategories"))children(cid)else d.array("children").objects().map(::category),watched=d.flag("isWatched"))
 }
 suspend fun discovery(kind:String,page:Int=1,tag:String=""):Page {
  require(page>0)
  val path=when(kind){
   "popular"->"/api/popular?page=$page"
   "unsolved"->"/api/unsolved?page=$page"
   "unread"->"/api/unread?page=$page"
   "tag"->{require(tag.isNotBlank());val url=FORUM.toHttpUrl().newBuilder().addPathSegments("api/tags").addPathSegment(tag).addQueryParameter("page",page.toString()).build();url.encodedPath+"?"+url.encodedQuery}
   else->throw IllegalArgumentException("רשימת דיונים לא מוכרת")
  }
  val d=request(path) as JSONObject
  val pages=d.obj("pagination").optInt("pageCount",1).coerceAtLeast(1)
  return Page(d.array("topics").objects().map(::topic).filter{it.id>0},d.obj("pagination").optInt("currentPage",page),pages,d.optInt("topicCount"),when(kind){"popular"->"פופולריים";"unsolved"->"לא נפתר";"unread"->"לא נקראו";else->"תגית: $tag"})
 }
 suspend fun tags():List<ForumTag> {
  val d=request("/api/tags") as JSONObject
  return d.array("tags").objects().map{ForumTag(it.optString("value"),it.optInt("score"))}.filter{it.name.isNotBlank()}
 }
 suspend fun thread(tid:Int,page:Int,index:Int=0):ThreadPage {
  require(tid>0&&page>0)
  val path=if(index>0)"/api/topic/$tid/_/$index" else "/api/topic/$tid?page=$page"
  val d=request(path) as JSONObject
  return withContext(Dispatchers.Default){ThreadPage(topic(d),d.array("posts").objects().map(::post),d.obj("pagination").optInt("currentPage",page),d.obj("pagination").optInt("pageCount",1).coerceAtLeast(1),d.flag("isFollowing"),d.flag("locked"),d.obj("privileges").optBoolean("topics:reply",true))}
 }
 suspend fun users(term:String):List<Account> {
  val u=FORUM.toHttpUrl().newBuilder().addPathSegments("api/users").addQueryParameter("query",term).build()
  val d=request(u.encodedPath+"?"+u.encodedQuery) as JSONObject
  return d.array("users").objects().filter{it.optInt("uid")>0}.map(::authenticatedAccount)
 }
 suspend fun profile(slug:String):PublicProfile {
  require(slug.isNotBlank())
  val path=FORUM.toHttpUrl().newBuilder().addPathSegments("api/user").addPathSegment(slug).build().encodedPath
  val profile=publicProfile(request(path) as JSONObject)
  check(profile.uid>0){"פרופיל המשתמש לא נמצא"}
  return profile
 }
 suspend fun profileSection(slug:String,section:String,page:Int=1):ProfileSection {
  require(slug.isNotBlank()&&page>0)
  require(section in setOf("topics","posts","best","controversial","followers","following","groups","shares","bookmarks"))
  val url=FORUM.toHttpUrl().newBuilder().addPathSegments("api/user").addPathSegment(slug).addPathSegment(section).addQueryParameter("page",page.toString()).build()
  val d=request(url.encodedPath+"?"+url.encodedQuery) as JSONObject
  val posts=d.array("posts").objects().map {p->UserPost(p.optInt("pid"),p.optInt("tid",p.obj("topic").optInt("tid")),plain(p.obj("topic").optString("titleRaw",p.obj("topic").optString("title"))),plain(p.optString("content")).take(260),p.optLong("timestamp"),p.optInt("votes",p.optInt("upvotes")-p.optInt("downvotes")))}.filter{it.pid>0&&it.tid>0}
  val people=d.array("users").objects().filter{it.optInt("uid")>0}.map{Account(it.optInt("uid"),plain(it.optString("displayname",it.optString("username"))),it.optString("userslug"),avatarUrl(it.optString("picture")),it.optString("status")=="online")}
  val pagination=d.obj("pagination")
  val topics=d.array("topics").objects().map(::topic).filter{it.id>0}
  val total=when(section){"topics"->d.obj("counts").optInt("topics");"posts"->d.obj("counts").optInt("posts");"followers"->d.obj("counts").optInt("followers");"following"->d.obj("counts").optInt("following");else->0}
  val count=when(section){"topics"->topics.size;"followers","following"->people.size;else->posts.size}
  val inferredPages=when{
   total>0&&section in setOf("topics","posts")->(total+19)/20
   total>0&&section in setOf("followers","following")&&count>=total->page
   count>=20->page+1
   else->page
  }
  return ProfileSection(topics,posts,people,d.array("groups").objects().map{plain(it.optString("name"))}.filter{it.isNotBlank()},page,maxOf(pagination.optInt("pageCount",1),inferredPages).coerceAtLeast(1))
 }
 suspend fun postRoute(pid:Int):Route {
  require(pid>0)
  // NodeBB's public post permalink API resolves to the topic without relying on v3 privileges.
  val permalink=try{request("/api/post/$pid") as? String}catch(e:CancellationException){throw e}catch(_:Exception){null}
  val permalinkRoute=permalink?.let(::routeFromLink)
  if(permalinkRoute?.kind=="topic")return permalinkRoute.copy(pid=pid)
  val data=(request("/api/v3/posts/$pid") as JSONObject).obj("response")
  val index=(request("/api/v3/posts/$pid/index") as JSONObject).obj("response").optInt("index",-1)
  val tid=data.optInt("tid");check(tid>0&&index>=0){"לא נמצא מיקום הפוסט בדיון"}
  return Route("topic",tid=tid,index=index+1,pid=pid)
 }
 suspend fun search(term:String,page:Int,cid:Int=0,by:String="",range:Int=0,searchIn:String="titlesposts"):Page {
  require(page>0&&searchIn in setOf("titlesposts","titles","posts"))
  val builder=FORUM.toHttpUrl().newBuilder().addPathSegments("api/search").addQueryParameter("term",term).addQueryParameter("page",page.toString()).addQueryParameter("in",searchIn).addQueryParameter("showAs","topics")
  if(cid>0)builder.addQueryParameter("categories",cid.toString())
  if(by.isNotBlank())builder.addQueryParameter("by",by.trim())
  if(range>0)builder.addQueryParameter("timeRange",range.toString())
  val url=builder.build()
  val d=request(url.encodedPath+"?"+url.encodedQuery) as JSONObject
  val topics=d.array("topics").objects().map(::topic).ifEmpty { d.array("posts").objects().map { p->val found=p.obj("topic");if(!found.has("user"))found.put("user",p.obj("user"));if(!found.has("category"))found.put("category",p.obj("category"));topic(found) } }.distinctBy { it.id }.filter { it.id>0 }
  return Page(topics,page,d.obj("pagination").optInt("pageCount",d.optInt("pageCount",1)).coerceAtLeast(1),d.optInt("matchCount"))
 }
 suspend fun notifications():List<Notice> = parseNotices((request("/api/v3/notifications") as JSONObject).obj("response"))
 suspend fun roomList(start:Int=0):List<ChatRoom> {require(start>=0);val d=(request("/api/v3/chats/?start=$start&perPage=20") as JSONObject).opt("response");return parseRooms(d)}
 suspend fun messages(room:Int,start:Int=0):List<ChatMessage> {require(room>0&&start>=0);return parseMessages((request("/api/v3/chats/$room/messages?start=$start") as JSONObject).obj("response").array("messages"))}
 suspend fun sendMessage(room:Int,text:String):Int {check(account.uid>0);require(room>0&&text.isNotBlank());val d=(request("/api/v3/chats/$room","POST",JSONObject().put("message",text)) as JSONObject).obj("response");val id=d.optInt("messageId",d.optInt("mid"));check(id>0){"לא התקבל אישור שליחה. בדקו את השיחה לפני ניסיון נוסף"};return id}
 suspend fun newChat(uid:Int):Int {check(account.uid>0);require(uid>0&&uid!=account.uid);val d=(request("/api/v3/chats/","POST",JSONObject().put("uids",JSONArray().put(uid))) as JSONObject).obj("response");val id=d.optInt("roomId");check(id>0){"לא התקבל מזהה שיחה"};return id}
 suspend fun vote(pid:Int,direction:Int){check(account.uid>0);require(pid>0&&direction in -1..1);request("/api/v3/posts/$pid/vote",if(direction==0)"DELETE"else "PUT",if(direction==0)null else JSONObject().put("delta",direction))}
 suspend fun bookmarkPost(pid:Int,undo:Boolean=false){check(account.uid>0);require(pid>0);request("/api/v3/posts/$pid/bookmark",if(undo)"DELETE"else "PUT")}
 suspend fun bookmarkTopicMainPost(tid:Int){check(account.uid>0);require(tid>0);val d=request("/api/topic/$tid?page=1") as JSONObject;val pid=d.optInt("mainPid",d.array("posts").optJSONObject(0)?.optInt("pid")?:0);check(pid>0){"לא נמצא הפוסט הראשון בדיון"};bookmarkPost(pid)}
 suspend fun reportPost(pid:Int,reason:String){check(account.uid>0);require(pid>0&&reason.trim().length in 5..500);request("/api/v3/flags/","POST",JSONObject().put("type","post").put("id",pid).put("reason",reason.trim()))}
 suspend fun markTopicRead(tid:Int,unread:Boolean=false){check(account.uid>0);require(tid>0);request("/api/v3/topics/$tid/read",if(unread)"DELETE"else "PUT")}
 suspend fun followUser(uid:Int,undo:Boolean=false){check(account.uid>0&&uid>0&&uid!=account.uid);request("/api/v3/users/$uid/follow",if(undo)"DELETE"else "PUT")}
 suspend fun watchCategory(cid:Int,undo:Boolean=false){check(account.uid>0&&cid>0);request("/api/v3/categories/$cid/watch",if(undo)"DELETE"else "PUT")}
 suspend fun rawPost(pid:Int):String {
  require(pid>0)
  val response=(request("/api/v3/posts/$pid/raw") as JSONObject).obj("response")
  check(response.has("content")){"לא ניתן לטעון את תוכן הפוסט לעריכה"}
  return response.optString("content")
 }
 suspend fun editPost(pid:Int,content:String){check(account.uid>0);require(pid>0&&content.trim().length>=minPost);request("/api/v3/posts/$pid","PUT",JSONObject().put("content",content))}
 suspend fun deletePost(pid:Int){check(account.uid>0);require(pid>0);request("/api/v3/posts/$pid/state","DELETE")}
 suspend fun follow(tid:Int,undo:Boolean=false){check(account.uid>0);require(tid>0);request("/api/v3/topics/$tid/follow",if(undo)"DELETE"else "PUT")}
 suspend fun markNoticeRead(nid:String){check(account.uid>0);val encoded=FORUM.toHttpUrl().newBuilder().addPathSegments("api/v3/notifications").addPathSegment(nid).addPathSegment("read").build().encodedPath;request(encoded,"PUT")}
 suspend fun upload(file:LocalFile,context:Context,onProgress:(Float)->Unit={}):String=jar.requestGate.withLock {withContext(Dispatchers.IO) {
  configUnlocked();verifyAccountUnlocked()
  check(account.uid>0&&csrf.isNotEmpty());check(!RuntimeSafety.offlineDemo){"העלאה חסומה במצב דמה"}
  require(file.size in 1..maxFileBytes){"הקובץ גדול מהמותר בפורום"};require(file.type in listOf("image/png","image/jpeg","image/webp","image/gif","application/pdf"))
  val content=object:RequestBody(){override fun contentType()=file.type.toMediaType();override fun contentLength()=file.size;override fun writeTo(sink:okio.BufferedSink){context.contentResolver.openInputStream(file.uri)?.use{input->val buffer=ByteArray(8192);var sent=0L;while(true){val n=input.read(buffer);if(n<0)break;sent+=n;check(sent<=maxFileBytes){"הקובץ גדול מהמותר בפורום"};sink.write(buffer,0,n);onProgress(sent.toFloat()/file.size)}}?:throw IOException("לא ניתן לקרוא את הקובץ המצורף")}}
  val name=file.name.replace(Regex("[\\r\\n\\\"\\\\/]"),"_").take(120)
  val body=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("files[]",name,content).build()
  val req=Request.Builder().url("$FORUM/api/post/upload").header("Accept","application/json").header("Origin",FORUM).header("x-csrf-token",csrf).post(body).build()
  client.newCall(req).awaitResponse().use {r->if(!r.isSuccessful)throw ForumHttpException(r.code,serverError(r.code,r.body?.string().orEmpty()));val d=JSONObject(r.body?.string().orEmpty()).obj("response");val url=d.array("images").optJSONObject(0)?.optString("url");val safe=url?.let(::safeLink)?:throw IOException("לא התקבל קישור תקין לקובץ שהועלה");safe.toString()}
 }}
 suspend fun publish(cid:Int,title:String,content:String,replyTid:Int=0,toPid:Int=0):PublishResult {
  check(account.uid>0){"נדרשת התחברות"};require(content.trim().length>=minPost&&content.length<=maxPost);if(replyTid==0)require(cid>0&&title.trim().length>=minTitle)
  // CSRF tokens are refreshed after login and can expire while the editor is open.
  val body=JSONObject().put("content",content);if(replyTid==0)body.put("cid",cid).put("title",title.trim())
  if(toPid>0)body.put("toPid",toPid)
  val d=(request(if(replyTid>0)"/api/v3/topics/$replyTid" else "/api/v3/topics/","POST",body) as JSONObject).obj("response")
  return parsePublished(d,replyTid)
 }
}
