package community.mitmachim.nativeapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.android.apksig.ApkVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Code/hash are learned from the verified APK, not from editable forum text. */
data class AppRelease(val versionCode:Int,val versionName:String,val sha256:String,val notes:String,val downloadUrl:String,val postId:Int) {
 fun toJson()=JSONObject().put("versionCode",versionCode).put("versionName",versionName).put("sha256",sha256)
  .put("notes",notes).put("downloadUrl",downloadUrl).put("postId",postId)
}
/** A permanent rejection must not trigger repeated background APK downloads. */
class UpdateRejectedException(message:String):IOException(message)
fun releaseVersionParts(name:String):List<Int>? {
 if(!Regex("^[0-9]{1,9}(?:\\.[0-9]{1,9}){1,5}$").matches(name))return null
 return name.split('.').map{it.toInt()}
}
fun compareReleaseVersions(a:String,b:String):Int {
 val left=releaseVersionParts(a)?:return 0
 val right=releaseVersionParts(b)?:return 0
 for(i in 0 until maxOf(left.size,right.size)){
  val comparison=(left.getOrNull(i)?:0).compareTo(right.getOrNull(i)?:0)
  if(comparison!=0)return comparison
 }
 return 0
}
fun updateDownloadError(code:Int)=when(code){
 403->"שרת הפורום חסם את הורדת העדכון (HTTP 403). יש להעלות לפוסט הראשון את קובץ ההפצה בסיומת ‎.ap."
 404->"קובץ העדכון לא נמצא בשרת (HTTP 404). יש להעלות אותו מחדש לפוסט הראשון."
 in 300..399->"הורדת העדכון הופנתה לכתובת שאינה מאושרת (HTTP $code). יש לפרסם קישור ישיר בפוסט הראשון."
 else->"הורדת העדכון נכשלה (HTTP $code). נסו שוב מאוחר יותר."
}
fun verifiedApkSigners(file:File,minSdk:Int=24):Set<String> {
 val result=try{ApkVerifier.Builder(file).setMinCheckedPlatformVersion(minSdk).build().verify()}
 catch(_:Exception){throw UpdateRejectedException("לא ניתן לאמת את החתימה הדיגיטלית של קובץ העדכון.")}
 if(!result.isVerified)throw UpdateRejectedException("הקובץ אינו APK בעל חתימה דיגיטלית תקינה. העדכון נחסם.")
 return result.signerCertificates.map{certificate->
  MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(""){"%02x".format(it)}
 }.toSet()
}

/** Update discovery is exclusively from the original author's first post. */
object AppUpdates {
 const val TOPIC_ID=101925
 const val AUTHOR_UID=31342
 const val PREF_AUTOMATIC="automaticAppUpdates"
 const val RELEASE_CERT="0af6f3e3379f1d10fe640537ce186f6288fff587d9e97e8367c7364344a45f77"
 private const val PREF_READY="readyAppUpdateV2"
 private const val MAX_APK_BYTES=80L*1024*1024
 private val filePattern=Regex("^/files/(?:[0-9]{10,20}-)?mitmachim-plus-beta-([0-9]{1,9}(?:\\.[0-9]{1,9}){1,5})-release\\.(ap|apk)$",RegexOption.IGNORE_CASE)
 private val shaPattern=Regex("^[0-9a-f]{64}$")
 private val downloadMutex=Mutex()
 private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
  .addInterceptor(ForumOriginGuard()).addNetworkInterceptor(ForumOriginGuard())
  .connectTimeout(15,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).callTimeout(5,TimeUnit.MINUTES).build()

 private fun safeDownloadUrl(value:String)=safeLink(value)?.takeIf{
  isForumUrl(it)&&it.host=="mitmachim.top"&&it.query==null&&it.fragment==null&&
   filePattern.matches(it.encodedPath.removePrefix("/assets/uploads"))&&it.encodedPath.startsWith("/assets/uploads/files/")
 }
 fun parseRelease(post:JSONObject):AppRelease? {
  if(post.optInt("uid",post.obj("user").optInt("uid"))!=AUTHOR_UID||post.flag("deleted")||post.optInt("pid")<=0)return null
  val candidates=post.array("uploads").let{a->(0 until a.length()).mapNotNull{i->
   val upload=a.opt(i) as? String?:return@mapNotNull null
   val match=filePattern.matchEntire(upload)?:return@mapNotNull null
   val url=safeDownloadUrl("$FORUM/assets/uploads$upload")?:return@mapNotNull null
   AppRelease(0,match.groupValues[1],"","גרסה חדשה פורסמה בפוסט הראשון של מתמחים+. הקובץ יאומת לפני ההתקנה.",url.toString(),post.optInt("pid"))
  }}
  // Prefer .ap over .apk for the same version, since the forum blocks APK downloads.
  return candidates.maxWithOrNull(Comparator{a,b->
   val version=compareReleaseVersions(a.versionName,b.versionName)
   if(version!=0)version else {
    val extension=a.downloadUrl.endsWith(".ap",true).compareTo(b.downloadUrl.endsWith(".ap",true))
    if(extension!=0)extension else a.downloadUrl.compareTo(b.downloadUrl)
   }
  })
 }
 fun releaseFromTopicPage(topic:JSONObject):AppRelease? {
  if(topic.optInt("tid")!=TOPIC_ID)return null
  val mainPid=topic.optInt("mainPid").takeIf{it>0}?:return null
  return topic.array("posts").objects().singleOrNull{it.optInt("pid")==mainPid}?.let(::parseRelease)
 }
 suspend fun latest(api:ForumApi):AppRelease? {
  val topic=api.updateTopicPage(1)
  check(topic.optInt("tid")==TOPIC_ID){"שרת העדכונים החזיר שרשור שאינו ערוץ ההפצה."}
  val main=topic.array("posts").objects().singleOrNull{it.optInt("pid")==topic.optInt("mainPid")}
   ?:throw IOException("לא נמצא הפוסט הראשון בערוץ העדכונים. נסו שוב.")
  if(main.optInt("uid",main.obj("user").optInt("uid"))!=AUTHOR_UID||main.flag("deleted"))
   throw IOException("לא ניתן לאמת את מחבר פוסט ההפצה. העדכון נחסם.")
  return releaseFromTopicPage(topic)
 }
 fun installedVersionCode(context:Context)=versionCode(context.packageManager.getPackageInfo(context.packageName,0))
 fun isNewer(context:Context,release:AppRelease):Boolean {
  val installed=context.packageManager.getPackageInfo(context.packageName,0)
  return if(release.versionCode>0)release.versionCode.toLong()>versionCode(installed)
   else compareReleaseVersions(release.versionName,installed.versionName.orEmpty())>0
 }
 fun readyFile(context:Context,release:AppRelease):File {
  val key=hex(MessageDigest.getInstance("SHA-256").digest(release.downloadUrl.toByteArray(Charsets.UTF_8)))
  return File(File(context.cacheDir,"app-updates"),"release-${key.take(24)}.apk")
 }
 fun cachedRelease(context:Context):AppRelease?=runCatching{
  val raw=context.getSharedPreferences("native-ui",Context.MODE_PRIVATE).getString(PREF_READY,null)?:return@runCatching null
  val d=JSONObject(raw)
  val url=safeDownloadUrl(d.optString("downloadUrl"))?:return@runCatching null
  val hash=d.optString("sha256")
  if(!shaPattern.matches(hash)||d.optInt("versionCode")<=0||releaseVersionParts(d.optString("versionName"))==null)return@runCatching null
  AppRelease(d.getInt("versionCode"),d.getString("versionName"),hash,d.optString("notes"),url.toString(),d.getInt("postId"))
 }.getOrNull()?.takeIf{isNewer(context,it)}

 /** Signature verification covers the entire downloaded APK, before exposing Install. */
 suspend fun download(context:Context,release:AppRelease):AppRelease=downloadMutex.withLock{withContext(Dispatchers.IO){
  require(isNewer(context,release)){"הגרסה הזו כבר מותקנת"}
  val url=safeDownloadUrl(release.downloadUrl)?:throw UpdateRejectedException("כתובת העדכון אינה שייכת לשרת ההפצה")
  val target=readyFile(context,release)
  cachedRelease(context)?.takeIf{it.downloadUrl==release.downloadUrl}?.let{cached->
   if(target.isFile&&runCatching{verifyReady(context,cached)}.isSuccess)return@withContext cached
  }
  val directory=target.parentFile?:throw IOException("לא נמצאה תיקייה לעדכון")
  if(!directory.isDirectory&&!directory.mkdirs())throw IOException("לא ניתן להכין מקום לעדכון")
  val partial=File(directory,"${target.name}.part")
  try{
   val request=Request.Builder().url(url).header("Accept","application/octet-stream").header("User-Agent","MitmachimNative/UpdaterV2").build()
   val hash=client.newCall(request).awaitResponse().use{response->
    if(!response.isSuccessful){
     if(response.code in 300..499)throw UpdateRejectedException(updateDownloadError(response.code))
     throw IOException(updateDownloadError(response.code))
    }
    val body=response.body?:throw IOException("שרת העדכונים החזיר קובץ ריק")
    val expected=body.contentLength()
    if(expected>MAX_APK_BYTES)throw UpdateRejectedException("קובץ העדכון גדול מהצפוי")
    val digest=MessageDigest.getInstance("SHA-256")
    var total=0L
    body.byteStream().use{input->FileOutputStream(partial).use{output->
     val buffer=ByteArray(32*1024)
     while(true){
      currentCoroutineContext().ensureActive()
      val count=input.read(buffer);if(count<0)break
      total+=count;if(total>MAX_APK_BYTES)throw UpdateRejectedException("קובץ העדכון גדול מהצפוי")
      digest.update(buffer,0,count);output.write(buffer,0,count)
     }
     output.fd.sync()
    }}
    if(total==0L||(expected>=0&&expected!=total))throw IOException("הורדת העדכון לא הושלמה. נסו שוב.")
    hex(digest.digest())
   }
   val code=verifyArchive(context,partial,release)
   val verified=release.copy(versionCode=code,sha256=hash)
   if(target.exists()&&!target.delete())throw IOException("לא ניתן להחליף קובץ עדכון ישן")
   if(!partial.renameTo(target))throw IOException("לא ניתן לשמור את קובץ העדכון")
   context.getSharedPreferences("native-ui",Context.MODE_PRIVATE).edit().putString(PREF_READY,verified.toJson().toString()).apply()
   verified
  }finally{partial.delete()}
 }}
 fun verifyReady(context:Context,release:AppRelease):File {
  val file=readyFile(context,release)
  if(!shaPattern.matches(release.sha256)||release.versionCode<=0||!file.isFile||file.length()<=0||file.length()>MAX_APK_BYTES)
   throw IOException("קובץ העדכון אינו זמין או טרם אומת. הורידו אותו מחדש.")
  val digest=MessageDigest.getInstance("SHA-256")
  file.inputStream().use{input->val buffer=ByteArray(32*1024);while(true){val count=input.read(buffer);if(count<0)break;digest.update(buffer,0,count)}}
  if(hex(digest.digest())!=release.sha256)throw IOException("קובץ העדכון השתנה לאחר האימות. הורידו אותו מחדש.")
  if(verifyArchive(context,file,release)!=release.versionCode)throw IOException("מספר הגרסה אינו תואם לקובץ העדכון.")
  return file
 }
 private fun verifyArchive(context:Context,file:File,release:AppRelease):Int {
  if(verifiedApkSigners(file,Build.VERSION.SDK_INT)!=setOf(RELEASE_CERT))throw UpdateRejectedException("קובץ העדכון אינו חתום במפתח ה־release המקורי. העדכון נחסם.")
  val pm=context.packageManager
  val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
  val archive=pm.getPackageArchiveInfo(file.absolutePath,flags)?:throw UpdateRejectedException("קובץ העדכון אינו APK תקין")
  val installed=pm.getPackageInfo(context.packageName,flags)
  if(archive.packageName!="community.mitmachim.reader"||archive.packageName!=context.packageName||versionCode(archive)<=versionCode(installed)||versionCode(archive)>Int.MAX_VALUE)
   throw UpdateRejectedException("הקובץ אינו עדכון חדש המתאים לאפליקציה המותקנת. לא תבוצע התקנה.")
  if(archive.versionName!=release.versionName||signers(installed)!=setOf(RELEASE_CERT))
   throw UpdateRejectedException("שם הגרסה בקובץ או חתימת האפליקציה המותקנת אינם תואמים. העדכון נחסם.")
  return versionCode(archive).toInt()
 }
 fun launchInstaller(context:Context,release:AppRelease){
  val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",readyFile(context,release))
  context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
   .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
 }
 private fun versionCode(info:PackageInfo):Long=if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
 private fun signers(info:PackageInfo):Set<String> {
  val signatures=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners else info.signatures
  return signatures?.map{hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))}?.toSet().orEmpty()
 }
 private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it)}
}
