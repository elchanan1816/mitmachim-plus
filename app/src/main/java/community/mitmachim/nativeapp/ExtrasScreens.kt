package community.mitmachim.nativeapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

fun openBrowser(context:Context,url:String,onError:(String)->Unit={}) {
 val safe=safeLink(url)?:return
 try{CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context,Uri.parse(safe.toString()))}
 catch(_:Exception){try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(safe.toString())))}catch(_:Exception){onError("לא נמצא דפדפן במכשיר לפתיחת הקישור")}}
}
fun shareText(context:Context,text:String){context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"שיתוף"))}
@Composable fun AboutScreen(vm:AppModel,onLink:(String)->Unit){
 val context=LocalContext.current
 val version=remember{context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty()}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
  item{Paper{Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){Surface(Modifier.size(108.dp),shape=RoundedCornerShape(30.dp),color=Color(0xFF007F89),shadowElevation=4.dp){Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF007F89),Color(0xFF235DA4)))),contentAlignment=Alignment.Center){Image(painterResource(R.drawable.ic_launcher_foreground),"סמל מתמחים+",Modifier.size(108.dp))}};Spacer(Modifier.height(12.dp));Text("מתמחים+",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("קהילת ידע וטכנולוגיה",color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(12.dp));Badge("גרסה $version")}}}
  item{AppUpdatePanel(vm)}
  item{Paper{Text("פיתוח האפליקציה",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(16.dp));Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
   Image(painterResource(R.drawable.developer_portrait),"תמונת המפתח רב יהודה פרחים",contentScale=ContentScale.Crop,modifier=Modifier.size(80.dp).clip(CircleShape))
   Column(Modifier.weight(1f)){Text("אפליקציה זו פותחה ע״י",style=MaterialTheme.typography.bodyLarge);Text("@רב יהודה פרחים",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)}
  };Spacer(Modifier.height(12.dp));Text("ובסיוע @הבריסקער רב",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(12.dp));Text("מפורום מתמחים טופ",style=MaterialTheme.typography.bodyLarge)}}
  item{Paper{Text("הקהילה, קרובה יותר",fontWeight=FontWeight.Bold);Text("קריאת דיונים, מדריכים וסיוע הדדי, עם ממשק עברי המותאם לטלפון ולנגן.",Modifier.padding(vertical=10.dp));OutlinedButton(onClick={onLink(FORUM)}){Text("לאתר מתמחים טופ")};Text("אפליקציה עצמאית ולא רשמית. התוכן שייך לפורום ולכותביו.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
 }
}
@Composable fun DraftsScreen(vm:AppModel){
 val revision=vm.draftRevision
 val drafts=remember(revision,vm.account.uid){vm.drafts()}
 var remove by remember{mutableStateOf<SavedDraft?>(null)}
 LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{SectionTitle("הטיוטות שלי","נושא חדש"){vm.open(Route("editor"))}}
  if(drafts.isEmpty())item{EmptyCard("אין טיוטות שמורות","בזמן כתיבה הטיוטה נשמרת אוטומטית במכשיר.",AppIcons.Edit)}
  items(drafts,key={it.key}){d->Paper{Text(d.title.ifBlank{if(d.mode=="edit")"עריכת פוסט"else if(d.tid>0)"תגובה לדיון"else "נושא ללא כותרת"},fontWeight=FontWeight.Bold);Text(d.content.take(140),maxLines=3,style=MaterialTheme.typography.bodyMedium);if(d.updated>0)Text(ago(d.updated),style=MaterialTheme.typography.bodySmall);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilledTonalButton(onClick={vm.open(Route(if(d.mode=="edit")"edit"else "editor",cid=d.cid,tid=d.tid,pid=d.toPid,draft=d.key,index=d.index))}){Text("המשך כתיבה")};OutlinedButton(onClick={remove=d}){Text("מחיקה")}}}}
 }
 remove?.let{d->AlertDialog(onDismissRequest={remove=null},title={Text("למחוק את הטיוטה?")},text={Text("הטקסט והקבצים המצורפים יוסרו מהטיוטה במכשיר.")},confirmButton={Button(onClick={vm.deleteDraft(d.key);remove=null}){Text("מחיקה")}},dismissButton={OutlinedButton(onClick={remove=null}){Text("ביטול")}})}
}
@Composable fun HistoryScreen(vm:AppModel){LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{SectionTitle("הדיונים שקראת לאחרונה")};if(vm.recent.isEmpty())item{EmptyCard("היסטוריית הקריאה ריקה","דיונים שתקראו יופיעו כאן להמשך קריאה.")};items(vm.recent,key={it.id}){TopicCard(it,vm)}}}
