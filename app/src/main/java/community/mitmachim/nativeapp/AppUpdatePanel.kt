package community.mitmachim.nativeapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun AppUpdatePanel(vm:AppModel){
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 var automatic by remember{mutableStateOf(AppUpdateWorker.isEnabled(context))}
 var release by remember{mutableStateOf(AppUpdates.cachedRelease(context))}
 var ready by remember{mutableStateOf(release?.let{AppUpdates.readyFile(context,it).isFile}==true)}
 var checking by remember{mutableStateOf(false)}
 var downloading by remember{mutableStateOf(false)}
 var installing by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("")}

 fun install(selected:AppRelease){
  installing=true
  scope.launch {
   try{
    withContext(Dispatchers.IO){AppUpdates.verifyReady(context,selected)}
    AppUpdates.launchInstaller(context,selected)
    status="אשרו את ההתקנה בחלון של Android."
   }catch(e:Exception){status=friendlyError(e)}finally{installing=false}
  }
 }
 val installPermission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){
  val selected=release
  if(selected!=null&&(Build.VERSION.SDK_INT<26||context.packageManager.canRequestPackageInstalls()))install(selected)
  else status="יש לאפשר לאפליקציה להתקין עדכונים בהגדרות המכשיר."
 }
 val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){
  if(!it)status="הבדיקה האוטומטית תפעל, אך ללא התראה מחוץ לאפליקציה."
 }

 Paper {
  Text("עדכוני האפליקציה",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
  Text("גרסה מותקנת: ${context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty()}",Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodyMedium)
  Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){
    Text("בדיקה והורדה אוטומטיות",fontWeight=FontWeight.SemiBold)
    Text("כבוי כברירת מחדל · ברשת שאינה מדודה · התקנה באישורכם",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
   Switch(checked=automatic,enabled=!vm.demo,onCheckedChange={value->
    automatic=value;AppUpdateWorker.setEnabled(context,value)
    status=if(value)"הבדיקה האוטומטית הופעלה. עדכונים ייבדקו ברשת שאינה מדודה."else "הבדיקה האוטומטית כובתה."
    if(value&&Build.VERSION.SDK_INT>=33&&!ForumNotificationWorker.hasPermission(context))notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
   })
  }
  OutlinedButton(enabled=!checking&&!downloading&&!vm.demo,onClick={
   checking=true;status="בודק גרסה חדשה…"
   scope.launch {try{
    val found=AppUpdates.latest(vm.api)
    release=found?.takeIf{AppUpdates.isNewer(context,it)}
    val cached=AppUpdates.cachedRelease(context)?.takeIf{it.downloadUrl==release?.downloadUrl}
    if(cached!=null)release=cached
    ready=cached?.let{AppUpdates.readyFile(context,it).isFile}==true
    status=when{found==null->"לא נמצא קובץ הפצה של מתמחים+ בפוסט הראשון. אין צורך בבלוק טכני — יש להעלות קובץ ‎.ap בשם הגרסה.";release==null->"האפליקציה מעודכנת. הגרסה האחרונה שפורסמה: ${found.versionName}.";else->"נמצא קובץ לגרסה ${release?.versionName}. לאחר ההורדה ייבדקו הגרסה והחתימה."}
   }catch(e:Exception){status=friendlyError(e)}finally{checking=false}}
  },modifier=Modifier.padding(top=12.dp)){Text(if(checking)"בודק…"else "בדוק עדכונים עכשיו")}

  release?.let{selected->
   HorizontalDivider(Modifier.padding(vertical=14.dp))
   Text("גרסה ${selected.versionName} זמינה",fontWeight=FontWeight.Bold)
   Text(selected.notes,Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodyMedium)
   if(ready)Button(enabled=!installing&&!downloading,onClick={
    if(Build.VERSION.SDK_INT>=26&&!context.packageManager.canRequestPackageInstalls()){
     status="יש לאפשר התקנת עדכונים לאפליקציה בהגדרות Android."
     installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))
    }else install(selected)
   },modifier=Modifier.padding(top=12.dp)){Text(if(installing)"בודק קובץ…"else "התקנת העדכון")}
   else Button(enabled=!downloading&&!checking,onClick={
    downloading=true;status="מוריד ומאמת את קובץ העדכון…"
    scope.launch {try{
     release=AppUpdates.download(context,selected)
     ready=true;status="קובץ העדכון מוכן להתקנה."
    }catch(e:Exception){status=friendlyError(e)}finally{downloading=false}}
   },modifier=Modifier.padding(top=12.dp)){Text(if(downloading)"מוריד…"else "הורדת העדכון")}
  }
  if(checking||downloading||installing)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=12.dp))
  if(status.isNotBlank())Text(status,Modifier.padding(top=10.dp),style=MaterialTheme.typography.bodySmall)
 }
}
