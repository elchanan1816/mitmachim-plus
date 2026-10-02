package community.mitmachim.nativeapp

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

@Composable fun ForumImage(url:String,alt:String,vm:AppModel){
 var failed by remember(url){mutableStateOf(false)};var loading by remember(url){mutableStateOf(true)};var retry by remember(url){mutableIntStateOf(0)};var show by remember(url){mutableStateOf(false)};val context=LocalContext.current
 // The frame never changes size on load/failure/retry, so content above the reader cannot jump.
 BoxWithConstraints(Modifier.fillMaxWidth()){
  Box(Modifier.fillMaxWidth().height(threadImageFrameHeight(maxWidth.value).dp),contentAlignment=Alignment.Center){
   if(failed)OutlinedButton(onClick={failed=false;loading=true;retry++}){Text("התמונה לא נטענה · ניסיון נוסף")}
   else key(url,retry){AsyncImage(model=ImageRequest.Builder(context).data(url).setParameter("retry",retry).build(),contentDescription=alt.ifBlank{"תמונה מהדיון — לחצו להגדלה"},contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize().clickable{show=true},onSuccess={loading=false},onError={failed=true;loading=false});if(loading)CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)}
  }
 }
 if(show)ImageViewer(url,vm){show=false}
}
@Composable fun ImageViewer(url:String,vm:AppModel,onClose:()->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var zoom by remember(url){mutableFloatStateOf(1f)};var x by remember(url){mutableFloatStateOf(0f)};var y by remember(url){mutableFloatStateOf(0f)}
 var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
 suspend fun download():Pair<ByteArray,String> = withContext(Dispatchers.IO){
  val app=context.applicationContext as CommunityApplication
  app.mediaClient.newCall(Request.Builder().url(url).build()).awaitResponse().use{response->
   check(response.isSuccessful){"לא ניתן להוריד את התמונה כרגע"};val body=response.body?:throw java.io.IOException("לא התקבלה תמונה")
   val mime=body.contentType()?.toString()?.substringBefore(';').orEmpty();check(mime.startsWith("image/")){"הקישור אינו מחזיר קובץ תמונה"}
   val output=java.io.ByteArrayOutputStream();body.byteStream().use{input->val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;check(output.size()+n<=40*1024*1024){"התמונה גדולה מדי לשמירה"};output.write(buffer,0,n)}}
   output.toByteArray() to mime
  }
 }
 val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")){uri->if(uri!=null)scope.launch{busy=true;try{val(bytes,_)=download();withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(bytes)}?:throw java.io.IOException("לא ניתן לשמור במיקום שנבחר")};vm.showMessage("התמונה נשמרה")}catch(e:Exception){error=friendlyError(e)}finally{busy=false}}}
 Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.systemBarsPadding()){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onClose){Icon(AppIcons.Close,"סגירה")};Text("תמונה מהדיון",Modifier.weight(1f));OutlinedButton(onClick={zoom=1f;x=0f;y=0f}){Text("איפוס")}}
   Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(url){detectTransformGestures{_,pan,scale,_->zoom=(zoom*scale).coerceIn(1f,5f);x=if(zoom>1f)(x+pan.x).coerceIn(-size.width*(zoom-1)/2,size.width*(zoom-1)/2)else 0f;y=if(zoom>1f)(y+pan.y).coerceIn(-size.height*(zoom-1)/2,size.height*(zoom-1)/2)else 0f}}){AsyncImage(model=url,contentDescription="תמונה מוגדלת",contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize().graphicsLayer{scaleX=zoom;scaleY=zoom;translationX=x;translationY=y},onError={error="התמונה לא נטענה. בדקו את החיבור ונסו שוב."})}
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(error.isNotEmpty())Text(error,Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error)
   Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceEvenly){
    OutlinedButton(enabled=!busy,onClick={save.launch(Uri.parse(url).lastPathSegment?.takeIf{it.contains('.')}?:"mitmachim-image.jpg")}){Text("שמירת תמונה")}
    OutlinedButton(enabled=!busy,onClick={scope.launch{busy=true;try{val(bytes,mime)=download();val extension=when(mime){"image/png"->"png";"image/webp"->"webp";"image/gif"->"gif";else->"jpg"};val file=withContext(Dispatchers.IO){File(context.cacheDir,"shared-images").apply{mkdirs()}.let{File(it,"image.$extension")}.apply{writeBytes(bytes)}};val uri=FileProvider.getUriForFile(context,context.packageName+".files",file);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"שיתוף תמונה"))}catch(e:Exception){error=friendlyError(e)}finally{busy=false}}}){Text("שיתוף תמונה")}
   }
  }}
 }
}
