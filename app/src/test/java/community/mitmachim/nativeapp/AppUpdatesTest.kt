package community.mitmachim.nativeapp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class AppUpdatesTest {
 private fun path(version:String="0.2.11",ext:String="ap")="/files/1790907635037-mitmachim-plus-beta-$version-release.$ext"
 private fun releasePost(uid:Int=AppUpdates.AUTHOR_UID,upload:String=path())=JSONObject()
  .put("uid",uid).put("pid",11).put("uploads",JSONArray().put(upload))
 private fun topic(posts:JSONArray)=JSONObject().put("tid",AppUpdates.TOPIC_ID).put("mainPid",11).put("posts",posts)

 @Test fun discoversAttachedReleaseWithoutMetadata(){
  val release=AppUpdates.parseRelease(releasePost())!!
  assertEquals("0.2.11",release.versionName)
  assertEquals(0,release.versionCode)
  assertEquals("",release.sha256)
  assertEquals("https://mitmachim.top/assets/uploads${path()}",release.downloadUrl)
  assertEquals(11,release.postId)
 }
 @Test fun ignoresBrokenDuplicatedAndHtmlMetadata(){
  for(content in listOf("", "notes=one\nnotes=two", "<p>MITMACHIM_PLUS_RELEASE_V1<br>versionCode=999</p>"))
   assertEquals("0.2.11",AppUpdates.parseRelease(releasePost().put("content",content))?.versionName)
 }
 @Test fun doesNotDiscoverLinksInTextOrReplies(){
  val main=releasePost().put("uploads",JSONArray()).put("content","<a href='${path()}'>Download</a>")
  val reply=releasePost().put("pid",12)
  assertNull(AppUpdates.parseRelease(main))
  assertNull(AppUpdates.releaseFromTopicPage(topic(JSONArray().put(main).put(reply))))
 }
 @Test fun selectsLatestNumericVersionAndPrefersAp(){
  val post=releasePost().put("uploads",JSONArray().put(path("0.2.9")).put(path("0.2.11","apk")).put(path("0.2.11")))
  assertTrue(AppUpdates.parseRelease(post)!!.downloadUrl.endsWith("0.2.11-release.ap"))
 }
 @Test fun comparesNumericVersionsWithoutLexicographicErrors(){
  assertTrue(compareReleaseVersions("0.2.11","0.2.9")>0)
  assertTrue(compareReleaseVersions("0.2.12","0.2.11")>0)
  assertTrue(compareReleaseVersions("0.2","0.17.5")<0)
  assertEquals(0,compareReleaseVersions("0.2","0.2.0"))
  assertNull(releaseVersionParts("0.2-beta"))
  assertNull(releaseVersionParts("0.999999999999"))
 }
 @Test fun rejectsUnsafeOrUnrelatedUploads(){
  for(upload in listOf("/files/../other.apk", "https://evil.test${path()}","/files/release.ap",path()+"?x=1",path()+"#x",path().replace("/files/","/files/%2e%2e/"),"/files/mitmachim-plus-beta-0.2-beta-release.ap"))
   assertNull(upload,AppUpdates.parseRelease(releasePost(upload=upload)))
  assertNull(AppUpdates.parseRelease(releasePost().put("uploads",JSONArray().put(JSONObject().put("url",path())))))
 }
 @Test fun requiresExpectedAuthorAndLivePost(){
  assertNull(AppUpdates.parseRelease(releasePost(uid=91)))
  assertNull(AppUpdates.parseRelease(releasePost().put("deleted",true)))
  assertNull(AppUpdates.parseRelease(releasePost().put("pid",0)))
  assertNotNull(AppUpdates.parseRelease(releasePost().removeUid()))
 }
 private fun JSONObject.removeUid():JSONObject {remove("uid");put("user",JSONObject().put("uid",AppUpdates.AUTHOR_UID));return this}
 @Test fun requiresUniqueOriginalPostInExpectedTopic(){
  val main=releasePost();val reply=releasePost().put("pid",12)
  assertEquals(11,AppUpdates.releaseFromTopicPage(topic(JSONArray().put(reply).put(main)))?.postId)
  assertNull(AppUpdates.releaseFromTopicPage(topic(JSONArray().put(reply))))
  assertNull(AppUpdates.releaseFromTopicPage(topic(JSONArray().put(main).put(main))))
  assertNull(AppUpdates.releaseFromTopicPage(topic(JSONArray().put(main)).put("tid",999)))
  assertNull(AppUpdates.releaseFromTopicPage(topic(JSONArray().put(main)).put("mainPid",0)))
 }
 @Test fun rejectsUnsignedOrInvalidApk(){
  val file=File.createTempFile("invalid-update-", ".apk")
  try {try {verifiedApkSigners(file);fail("Invalid APK accepted")}catch(_:IOException){}} finally {file.delete()}
 }
 @Test fun explainsBlockedMissingAndRedirectedDownloads(){
  assertTrue(updateDownloadError(403).contains("403"));assertTrue(updateDownloadError(403).contains(".ap"))
  assertTrue(updateDownloadError(404).contains("404"));assertTrue(updateDownloadError(302).contains("302"))
 }
 @Test fun onlineStatusIsReadFromForumProfiles(){
  assertTrue(publicProfile(JSONObject("""{"uid":42,"username":"writer","status":"online"}""")).online)
  assertTrue(post(JSONObject("""{"pid":12,"user":{"uid":42,"status":"online"}}""")).online)
  assertFalse(post(JSONObject("""{"pid":13,"user":{"uid":42,"status":"offline"}}""")).online)
 }
}
