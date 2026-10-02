package community.mitmachim.nativeapp

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class ForumNotificationWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result {
  val app=applicationContext as CommunityApplication;val prefs=app.getSharedPreferences("native-ui",Context.MODE_PRIVATE)
  if(RuntimeSafety.offlineDemo||!prefs.getBoolean("backgroundAlerts",false))return Result.success()
  return try{
   val api=ForumApi(app.session);val account=api.config();if(account.uid<=0)return Result.success()
   val unread=api.notifications().filter{!it.read&&it.id.isNotBlank()};val ids=unread.map{it.id}.toSet()
   val key="alert-baseline-${account.uid}";val known=prefs.getStringSet(key,null)
   if(!prefs.getBoolean("backgroundAlerts",false)||RuntimeSafety.offlineDemo)return Result.success()
   if(known!=null){val fresh=unread.filter{it.id !in known};val latest=fresh.firstOrNull()
    if(latest!=null&&hasPermission(app)){
     val manager=NotificationManagerCompat.from(app)
     if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel(CHANNEL,"עדכוני הפורום",NotificationManager.IMPORTANCE_DEFAULT))
     val intent=Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_notifications",true)
     val pending=PendingIntent.getActivity(app,51,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
     val publicNote=NotificationCompat.Builder(app,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("עדכון חדש ממתמחים+").build()
     val notification=NotificationCompat.Builder(app,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("${fresh.size} עדכונים חדשים בפורום").setContentText(latest.text).setStyle(NotificationCompat.BigTextStyle().bigText(latest.text)).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicNote).setContentIntent(pending).setAutoCancel(true).build()
     if(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(app,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)manager.notify(51,notification)
    }
   }
   if(prefs.getBoolean("backgroundAlerts",false))prefs.edit().putStringSet(key,ids).apply()
   Result.success()
  }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){Result.retry()}
 }
 companion object {
  private const val NAME="forum-notification-poll";private const val CHANNEL="forum-updates"
  fun hasPermission(context:Context)=NotificationManagerCompat.from(context).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
  fun enable(context:Context){if(RuntimeSafety.offlineDemo)return;context.getSharedPreferences("native-ui",Context.MODE_PRIVATE).edit().putBoolean("backgroundAlerts",true).apply();val request=PeriodicWorkRequestBuilder<ForumNotificationWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME,ExistingPeriodicWorkPolicy.KEEP,request)}
  fun disable(context:Context){val prefs=context.getSharedPreferences("native-ui",Context.MODE_PRIVATE);val edit=prefs.edit().putBoolean("backgroundAlerts",false);prefs.all.keys.filter{it.startsWith("alert-baseline-")}.forEach{edit.remove(it)};edit.apply();WorkManager.getInstance(context).cancelUniqueWork(NAME);NotificationManagerCompat.from(context).cancel(51)}
 }
}

@Composable fun BackgroundAlertsSettings(vm:AppModel){val context=LocalContext.current;var enabled by remember{mutableStateOf(vm.prefs.getBoolean("backgroundAlerts",false))};var status by remember{mutableStateOf("")}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted){ForumNotificationWorker.enable(context);enabled=true;status="בדיקה מחזורית הופעלה"}else status="לא ניתנה הרשאה להתראות"}
 Paper{Text("התראות ברקע",fontWeight=FontWeight.Bold);Text("בדיקה ישירה מול הפורום בערך כל 15 דקות או יותר, בהתאם להגבלות Android והסוללה. זה אינו Push מיידי ולא מחייב שירותי Google. ההתראות מתחילות אחרי קביעת נקודת ההתחלה בבדיקה הראשונה.",Modifier.padding(vertical=10.dp),style=MaterialTheme.typography.bodySmall)
 Row{Text("בדיקה מחזורית",Modifier.weight(1f));Switch(checked=enabled,onCheckedChange={value->if(!value){ForumNotificationWorker.disable(context);enabled=false}else if(vm.account.uid<=0){status="יש להתחבר לפני הפעלת התראות"}else if(Build.VERSION.SDK_INT>=33&&!ForumNotificationWorker.hasPermission(context)){permission.launch(Manifest.permission.POST_NOTIFICATIONS)}else if(!ForumNotificationWorker.hasPermission(context)){status="יש לאפשר התראות בהגדרות Android"}else{ForumNotificationWorker.enable(context);enabled=true}},enabled=!vm.demo)}
 if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
 }
}
