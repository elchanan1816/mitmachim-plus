package community.mitmachim.nativeapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class AppUpdateWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result {
  val app=applicationContext as CommunityApplication
  if(RuntimeSafety.offlineDemo||!isEnabled(app))return Result.success()
  return try{
   val release=AppUpdates.latest(ForumApi(app.session))?:return Result.success()
   if(!AppUpdates.isNewer(app,release))return Result.success()
   val verified=AppUpdates.download(app,release)
   if(isEnabled(app))notifyReady(app,verified)
   Result.success()
  }catch(e:CancellationException){throw e}
   catch(_:UpdateRejectedException){Result.success()}
   catch(_:Exception){Result.retry()}
 }

 private fun notifyReady(context:Context,release:AppRelease){
  if(!ForumNotificationWorker.hasPermission(context))return
  val manager=NotificationManagerCompat.from(context)
  if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel(CHANNEL,"עדכוני האפליקציה",NotificationManager.IMPORTANCE_DEFAULT))
  val intent=Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_updates",true)
  val pending=PendingIntent.getActivity(context,72,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_notification)
   .setContentTitle("גרסה ${release.versionName} מוכנה להתקנה")
   .setContentText("פתחו את האפליקציה להשלמת העדכון")
   .setContentIntent(pending).setAutoCancel(true).build()
  // Notification permission may be revoked between the check and delivery.
  try{manager.notify(72,notification)}catch(_:SecurityException){}
 }

 companion object {
  private const val NAME="mitmachim-app-updates"
  private const val CHANNEL="app-releases"
  fun isEnabled(context:Context)=context.getSharedPreferences("native-ui",Context.MODE_PRIVATE).getBoolean(AppUpdates.PREF_AUTOMATIC,false)
  fun scheduleIfEnabled(context:Context){if(isEnabled(context))schedule(context)}
  fun setEnabled(context:Context,enabled:Boolean){
   context.getSharedPreferences("native-ui",Context.MODE_PRIVATE).edit().putBoolean(AppUpdates.PREF_AUTOMATIC,enabled).apply()
   if(enabled)schedule(context)else{WorkManager.getInstance(context).cancelUniqueWork(NAME);NotificationManagerCompat.from(context).cancel(72)}
  }
  private fun schedule(context:Context){
   val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
   val request=PeriodicWorkRequestBuilder<AppUpdateWorker>(24,TimeUnit.HOURS).setConstraints(constraints).build()
   WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.KEEP,request)
  }
 }
}
