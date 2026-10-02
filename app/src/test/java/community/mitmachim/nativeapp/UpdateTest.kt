package community.mitmachim.nativeapp

import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.net.ConnectException
import javax.net.ssl.SSLException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateTest {
 @Test fun storedSubdomainCookieNeverMovesToRoot(){
  val cookie=restoreCookie(JSONObject().put("origin","https://files.mitmachim.top").put("cookie","session=test; Path=/; Secure; HttpOnly"))!!
  assertTrue(cookie.hostOnly);assertTrue(cookie.matches("https://files.mitmachim.top/a".toHttpUrl()));assertFalse(cookie.matches(FORUM.toHttpUrl()))
  assertNull(restoreCookie(JSONObject().put("origin","https://evil.test").put("cookie","session=test")))
 }
 @Test fun legacyCookiesKeepTheirRootScope(){val cookie=restoreCookie(JSONObject().put("cookie","session=test; Path=/; Secure"))!!;assertEquals("mitmachim.top",cookie.domain);assertTrue(cookie.hostOnly)}
 @Test fun numericPinnedFlagIsRecognized(){assertTrue(topic(JSONObject().put("pinned",1)).pinned);assertFalse(topic(JSONObject().put("pinned",0)).pinned)}
 @Test fun publicProfileIncludesVisibleForumCounts(){val profile=publicProfile(JSONObject("""{"uid":14,"username":"writer","profileviews":72,"lastonline":5000,"counts":{"topics":8,"posts":90,"followers":4,"following":2,"shares":1},"groups":[{"name":"מדריכים"}],"isFollowing":true}"""));assertEquals(72,profile.views);assertEquals(4,profile.followers);assertEquals(2,profile.following);assertEquals(listOf("מדריכים"),profile.groups);assertTrue(profile.isFollowing)}
 @Test fun replyAndResolutionMetadataArePreserved(){val reply=post(JSONObject("""{"pid":33,"index":2,"toPid":21,"bookmarked":true,"user":{"username":"a"}}"""));assertEquals(21,reply.toPid);assertTrue(reply.bookmarked);assertTrue(topic(JSONObject("""{"tid":5,"isSolved":true,"locked":1}""")).solved)}
 @Test fun errorsAreReadableHebrewAndNeverLeakTransportDetails(){
  for(e in listOf(UnknownHostException("Unable to resolve host SECRET"),SocketTimeoutException("Read timed out SECRET"),SSLException("certificate SECRET"),IllegalStateException("SECRET"))){val value=friendlyError(e);assertTrue(value.any{it in '\u0590'..'\u05ff'});assertFalse(value.contains("SECRET"))}
 }
 @Test fun onlyTransientReadFailuresMayBeRetried(){
  assertTrue(retryableReadFailure(SocketTimeoutException()))
  assertTrue(retryableReadFailure(ConnectException()))
  assertTrue(retryableReadFailure(ForumHttpException(503,"temporarily unavailable")))
  assertFalse(retryableReadFailure(ForumHttpException(429,"rate limited")))
  assertFalse(retryableReadFailure(UnknownHostException()))
  assertFalse(retryableReadFailure(SSLException("certificate")))
 }
 @Test fun forumSubdomainsRequireADotBoundaryAndHttps(){
  assertTrue(isForumUrl("https://files.mitmachim.top/a".toHttpUrl()));assertTrue(isForumUrl("https://mitmachim.top/a".toHttpUrl()))
  for(u in listOf("https://mitmachim.top.evil.test","https://evilmitmachim.top","http://mitmachim.top","https://mitmachim.top:444","https://user:secret@mitmachim.top"))assertFalse(u,isForumUrl(u.toHttpUrl()))
 }
 @Test fun mediaIsAutomaticButPrivateAddressesAndCredentialsAreRejected(){
  assertTrue(isMediaUrl("https://images.example.com/photo.jpg".toHttpUrl()))
  for(u in listOf("http://example.com/a","https://127.0.0.1/a","https://192.168.1.1/a","https://10.0.0.2/a","https://localhost/a","https://user:password@example.com/a"))assertFalse(isMediaUrl(u.toHttpUrl()))
 }
 @Test fun quotedReplyIncludesOriginalPostLinkAndContent(){val post=Post(52,3,"חבר","<p>תוכן מקורי</p>",1,false);val quote=quotePost(post);assertTrue(quote.contains("/post/52"));assertTrue(quote.contains("> תוכן מקורי"))}
 @Test fun attachmentsBecomeTilesButOrdinaryLinksStayInline(){val fragments=orderedPostFragments("<p>הנה <a href='/assets/file.pdf'>מדריך</a> וגם <a href='/topic/3'>קישור</a></p>");assertEquals(3,fragments.size);assertEquals("מדריך",splitContent(fragments[1]).attachments.single().second);assertTrue(splitContent(fragments[2]).html.contains("קישור"))}
 @Test fun linksKeepExactPostAndPage(){val r=routeFromLink("https://mitmachim.top/topic/42/slug/75");assertEquals(42,r?.tid);assertEquals(75,r?.index);assertEquals(3,routeFromLink("/topic/42/test?page=3")?.page);assertNull(routeFromLink("https://mitmachim.top.evil.test/topic/42/a/2"));assertNull(routeFromLink("/auth/google/callback"))}
 @Test fun queueIsSuccessWithoutInventingPostId(){val r=parsePublished(JSONObject("""{"queued":true,"id":"reply-123"}"""),42);assertTrue(r.queued);assertEquals(0,r.pid);assertEquals(42,r.tid)}
 @Test fun publishedReplyHasExactOneBasedIndex(){val r=parsePublished(JSONObject("""{"tid":42,"pid":9,"index":22}"""),42);assertEquals(23,r.index);assertEquals(9,r.pid);assertFalse(r.queued)}
 @Test fun ambiguousPublicationNeverClaimsSuccess(){try{parsePublished(JSONObject("""{"tid":42}"""),42);fail("missing pid accepted")}catch(_:java.io.IOException){}}
 @Test fun permissionsDoNotMasqueradeAsExpiredLogin(){assertTrue(serverError(403,"[[error:no-privileges]]").contains("הרשאה"));assertTrue(serverError(401,"").contains("החיבור לחשבון פג"));assertFalse(serverError(500,"<h1>Internal stack SECRET</h1>").contains("SECRET"))}
 private class MemorySession:ForumSession {override val requestGate=Mutex();override fun clear(){};override fun loadForRequest(url:HttpUrl)=emptyList<Cookie>();override fun saveFromResponse(url:HttpUrl,cookies:List<Cookie>){} }
 private class Transport(var publication:String="""{"tid":42,"pid":7,"index":20}"""):Interceptor {
  val requests=mutableListOf<Request>();var rejectWrite=false;var authenticated=true;var failReadOnce=false
  override fun intercept(chain:Interceptor.Chain):Response {
   val req=chain.request();synchronized(requests){requests.add(req)}
   val path=req.url.encodedPath
   if(path=="/api/recent"&&failReadOnce){failReadOnce=false;throw SocketTimeoutException("test timeout")}
   val body=when(path){
    "/api/config"->"""{"loggedIn":$authenticated,"uid":${if(authenticated)42 else 0},"csrf_token":"test-csrf","maximumFileSize":40000}"""
    "/api/self"->if(authenticated)"""{"uid":42,"username":"test"}"""else "{}"
    "/api/topic/37"->"""{"tid":37,"mainPid":77,"posts":[{"pid":77}]}"""
    LOGIN_PATH->"{}"
    else->if(rejectWrite)"""{"status":{"message":"[[error:no-privileges]]"}}"""else """{"response":$publication}"""
   }
   val code=if(path=="/api/self"&&!authenticated)401 else if(rejectWrite&&path.startsWith("/api/v3/topics"))403 else 200
   return Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("test").header("Content-Type","application/json").body(body.toResponseBody("application/json".toMediaType())).build()
  }
 }
 @Test fun publicationRefreshesSessionAndUsesReplyParentWithoutRetry()=runBlocking {
  val transport=Transport();val api=ForumApi(MemorySession(),transport);api.login("test","fake-test-password");transport.requests.clear()
  val result=api.publish(17,"","תגובה לבדיקה",42,19)
  assertEquals(listOf("/api/config","/api/self","/api/v3/topics/42"),transport.requests.map{it.url.encodedPath});assertEquals(7,result.pid)
  val req=transport.requests.last();assertEquals("POST",req.method);assertEquals("test-csrf",req.header("x-csrf-token"));val buffer=okio.Buffer();req.body!!.writeTo(buffer);assertEquals(19,JSONObject(buffer.readUtf8()).getInt("toPid"))
 }
 @Test fun rejectedWriteIsNotAutomaticallyRepeated()=runBlocking {
  val transport=Transport();val api=ForumApi(MemorySession(),transport);api.login("test","fake-test-password");transport.requests.clear();transport.rejectWrite=true
  try{api.publish(17,"","תגובה",42);fail("rejected publication accepted")}catch(e:ForumHttpException){assertEquals(403,e.status)}
  assertEquals(1,transport.requests.count{it.method=="POST"})
 }
 @Test fun transientFeedReadGetsOneRetry()=runBlocking {
  val transport=Transport();transport.failReadOnce=true;val api=ForumApi(MemorySession(),transport)
  api.feed()
  assertEquals(2,transport.requests.count{it.url.encodedPath=="/api/recent"})
 }
 @Test fun expiredSessionNeverReachesPublication()=runBlocking {
  val transport=Transport();val api=ForumApi(MemorySession(),transport);api.login("test","fake-test-password");transport.requests.clear();transport.authenticated=false
  try{api.publish(17,"","תגובה",42);fail("expired session wrote")}catch(e:ForumHttpException){assertEquals(401,e.status)}
  assertEquals(0,transport.requests.count{it.method=="POST"})
 }
 @Test fun queuedPublicationIsRecognizedThroughApi()=runBlocking {
  val transport=Transport("""{"queued":true,"id":"reply-123"}""");val api=ForumApi(MemorySession(),transport);api.login("test","fake-test-password");assertTrue(api.publish(17,"","תגובה",42).queued)
 }
 @Test fun reportUsesForumFlagEndpointAndReasonOnce()=runBlocking {
  val transport=Transport();val api=ForumApi(MemorySession(),transport);api.login("test","fake-test-password");transport.requests.clear()
  api.reportPost(37,"תוכן פוגעני")
  val posts=transport.requests.filter{it.method=="POST"}
  assertEquals(1,posts.size);assertEquals("/api/v3/flags/",posts.single().url.encodedPath)
  val buffer=okio.Buffer();posts.single().body!!.writeTo(buffer)
  val data=JSONObject(buffer.readUtf8());assertEquals("post",data.getString("type"));assertEquals(37,data.getInt("id"));assertEquals("תוכן פוגעני",data.getString("reason"))
 }
 @Test fun localTopicCanBeAddedToAccountWithoutDeletingLocalCopy()=runBlocking {
  val transport=Transport();val api=ForumApi(MemorySession(),transport);api.login("test","fake-test-password");transport.requests.clear()
  api.bookmarkTopicMainPost(37)
  assertEquals(listOf("/api/topic/37","/api/config","/api/self","/api/v3/posts/77/bookmark"),transport.requests.map{it.url.encodedPath})
  assertEquals("PUT",transport.requests.last().method)
 }
 @Test fun searchFiltersAreSentAsEncodedQueryParameters()=runBlocking {
  val transport=Transport();val api=ForumApi(MemorySession(),transport)
  api.search("מפתח חתימה",2,17,"tester",30,"titles")
  val url=transport.requests.last().url
  assertEquals("מפתח חתימה",url.queryParameter("term"));assertEquals("17",url.queryParameter("categories"));assertEquals("tester",url.queryParameter("by"));assertEquals("30",url.queryParameter("timeRange"));assertEquals("titles",url.queryParameter("in"))
 }
}
