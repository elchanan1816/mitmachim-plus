package community.mitmachim.nativeapp

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID

object DraftRules {
 fun validate(title:String,content:String,category:Int,reply:Boolean,minTitle:Int=10,minPost:Int=2,maxPost:Int=327670):String? = when {
  !reply&&category<=0->"בחרו קטגוריה לפרסום"
  !reply&&title.trim().length<minTitle->"הכותרת צריכה להכיל לפחות $minTitle תווים"
  title.length>255->"הכותרת ארוכה מ־255 תווים"
  content.trim().length<minPost->"יש לכתוב לפחות $minPost תווים"
  content.length>maxPost->"התוכן ארוך מהמגבלה של הפורום"
  else->null
 }
}
data class LocalFile(val uri:Uri,val name:String,val size:Long,val type:String,val uploadedUrl:String?=null)
fun attachmentMarkdown(name:String,type:String,url:String):String {val cleanName=name.replace(Regex("[\\[\\]\\r\\n]"),"_");val link=safeLink(url)?:throw IllegalArgumentException("קישור קובץ לא תקין");return "${if(type.startsWith("image/"))"!"else ""}[$cleanName](<${link.toString().replace(">","%3E").replace("<","%3C")}>)"}
fun markdownToHtml(source:String):String {val ext=listOf(TablesExtension.create(),StrikethroughExtension.create());val html=HtmlRenderer.builder().extensions(ext).escapeHtml(true).sanitizeUrls(true).build().render(Parser.builder().extensions(ext).build().parse(source));return normalizeSpoilerMarkup(html,markdownSyntax=true)}
fun fileJson(file:LocalFile)=JSONObject().put("uri",file.uri.toString()).put("name",file.name).put("size",file.size).put("type",file.type).put("uploadedUrl",file.uploadedUrl)
fun restoreFiles(array:JSONArray)=array.objects().mapNotNull{d->runCatching{LocalFile(Uri.parse(d.getString("uri")),d.getString("name"),d.getLong("size"),d.getString("type"),d.optString("uploadedUrl").takeIf{it.isNotBlank()&&it!="null"})}.getOrNull()}

@Composable fun EditorScreen(vm:AppModel,onLink:(String)->Unit){
 val previewFocus=LocalFocusManager.current
 val context=LocalContext.current;val scope=rememberCoroutineScope();val route=vm.route;val edit=route.kind=="edit";val reply=route.tid>0;val key=route.draft
 val restored=remember(key){runCatching{JSONObject(vm.prefs.getString(key,"{}")?:"{}")}.getOrDefault(JSONObject())}
 var title by rememberSaveable(key){mutableStateOf(restored.optString("title"))}
 val quickDraft=key==quickReplyDraftKey(vm.account.uid,route.tid)
 // An inline draft can change between visits; do not revive an older full-editor saved-state snapshot.
 var text by if(quickDraft)remember(key){mutableStateOf(TextFieldValue(restored.optString("content",route.query)))}
  else rememberSaveable(key,stateSaver=TextFieldValue.Saver){mutableStateOf(TextFieldValue(restored.optString("content",route.query)))}
 var cid by rememberSaveable(key){mutableIntStateOf(restored.optInt("cid",route.cid))}
 var attachments by remember(key){mutableStateOf(restoreFiles(restored.array("files")))}
 var publicationPending by if(quickDraft)remember(key){mutableStateOf(restored.optBoolean("submissionPending"))}
  else rememberSaveable(key){mutableStateOf(restored.optBoolean("submissionPending"))}
 var menu by remember{mutableStateOf(false)};var categoryQuery by remember{mutableStateOf("")}
 var preview by rememberSaveable{mutableStateOf(false)};var status by remember{mutableStateOf("")}
 var busy by remember{mutableStateOf(false)};var progress by remember{mutableFloatStateOf(0f)};var submitted by remember{mutableStateOf(false)}
 var loadingOriginal by remember(key){mutableStateOf(edit&&!restored.has("content"))}
 var originalFailed by remember(key){mutableStateOf(false)}
 var rawRevision by remember(key){mutableIntStateOf(0)}
 LaunchedEffect(edit,route.pid,key,rawRevision){
  if(edit&&!restored.has("content")){
   loadingOriginal=true;originalFailed=false
   try{val raw=vm.api.rawPost(route.pid);text=TextFieldValue(raw,TextRange(raw.length));status=""}
   catch(e:Exception){originalFailed=true;status=friendlyError(e)}
   finally{loadingOriginal=false}
  }
 }
 val all=(vm.directory as? Load.Ready)?.value?.let(::flatten).orEmpty()
 fun snapshot()=JSONObject().put("title",title).put("content",text.text).put("cid",cid).put("tid",route.tid).put("toPid",route.pid).put("mode",route.kind).put("index",route.index).put("submissionPending",publicationPending).put("files",JSONArray().apply{attachments.forEach{put(fileJson(it))}})
 fun save(){if(!submitted&&key.isNotEmpty()&&(!edit||(!loadingOriginal&&!originalFailed))){if(title.isNotBlank()||text.text.isNotBlank()||attachments.isNotEmpty())vm.saveDraft(key,snapshot())else if(vm.prefs.contains(key))vm.deleteDraft(key)}}
 val latestSave by rememberUpdatedState({save()})
 LaunchedEffect(title,text.text,cid,attachments){delay(400);save()}
 DisposableEffect(key){onDispose{latestSave();vm.sending=false}}
 BackHandler(busy){vm.showMessage("השליחה בעיצומה. המתינו לסיום הפעולה.")}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->scope.launch{
  busy=true;vm.sending=true
  val copied=mutableListOf<LocalFile>()
  try {withContext(Dispatchers.IO){uris.forEach{uri->
   val type=context.contentResolver.getType(uri).orEmpty();require(type in listOf("image/png","image/jpeg","image/webp","image/gif","application/pdf")){"ניתן לצרף תמונות וקובצי PDF"}
   var name="קובץ";context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())name=it.getString(0)}
   val directory=File(context.filesDir,"draft-attachments").apply{mkdirs()};val file=File(directory,UUID.randomUUID().toString())
   try{context.contentResolver.openInputStream(uri)?.use{input->file.outputStream().use{output->val buffer=ByteArray(8192);var count=0L;while(true){val n=input.read(buffer);if(n<0)break;count+=n;require(count<=vm.api.maxFileBytes){"הקובץ גדול מהמותר בפורום"};output.write(buffer,0,n)}}}?:throw java.io.IOException("לא ניתן לקרוא את הקובץ");require(file.length()>0){"הקובץ ריק"};copied.add(LocalFile(Uri.fromFile(file),name,file.length(),type))}catch(e:Exception){file.delete();throw e}
  }};attachments=attachments+copied;save();status="הקבצים צורפו ויישלחו עם הפוסט"}catch(e:Exception){copied.forEach{vm.deleteAttachment(it.uri)};status=friendlyError(e)}finally{busy=false;vm.sending=false}
 }}
 fun wrap(before:String,after:String=""){val a=text.selection.min;val b=text.selection.max;val selected=text.text.substring(a,b).ifEmpty{"טקסט"};val inserted=before+selected+after;text=TextFieldValue(text.text.replaceRange(a,b,inserted),TextRange(a+before.length,a+before.length+selected.length))}
 fun insertSpoiler(){val a=text.selection.min;val b=text.selection.max;val selected=text.text.substring(a,b).trim().ifEmpty{"טקסט מוסתר"}.replace(Regex("\\s*\\n\\s*")," ");val inserted="\n\n>! $selected\n\n";val result=text.text.replaceRange(a,b,inserted);text=TextFieldValue(result,TextRange((a+5).coerceAtMost(result.length)));save()}
 fun send(){
  if(loadingOriginal||originalFailed){status="יש לטעון תחילה את תוכן הפוסט המקורי";return}
  val pendingLinks=attachments.filter{it.uploadedUrl==null}.joinToString("\n"){attachmentMarkdown(it.name,it.type,"$FORUM/assets/uploads/attachment")}
  val invalid=DraftRules.validate(title,text.text+pendingLinks,cid,reply,vm.api.minTitle,vm.api.minPost,vm.api.maxPost)
  if(invalid!=null){status=invalid;return}
  if(vm.account.uid<=0){save();vm.showMessage("הטיוטה נשמרה. היכנסו לחשבון כדי לשלוח אותה.");vm.open(Route("login"));return}
  if(vm.demo){status="הפעולה חסומה בסביבת הבדיקות";return}
  busy=true;vm.sending=true;save()
  scope.launch{try{
   val pending=attachments.filter{it.uploadedUrl==null}
   pending.forEachIndexed{index,file->
    status="מעלה קובץ ${index+1} מתוך ${pending.size}…"
    val url=vm.api.upload(file,context){fraction->progress=(index+fraction)/pending.size}
    attachments=attachments.map{if(it.uri==file.uri)it.copy(uploadedUrl=url)else it}
    val body=text.text+"\n\n"+attachmentMarkdown(file.name,file.type,url);text=TextFieldValue(body,TextRange(body.length));save()
   }
   status=if(edit)"שומר את השינויים…"else "שולח לפורום…"
   publicationPending=true;save()
   if(edit){
    vm.api.editPost(route.pid,text.text)
    submitted=true;vm.deleteDraft(key);vm.showMessage("הפוסט עודכן")
    vm.completeEditing(route.tid,route.index,route.pid)
   }else{
    val result=vm.api.publish(cid,title,text.text,route.tid,route.pid)
    submitted=true;vm.deleteDraft(key)
    vm.showMessage(if(result.queued)"התוכן נשלח וממתין לאישור מנהלי הפורום"else if(reply)"התגובה פורסמה"else "הנושא פורסם")
    vm.completePublishing(result)
   }
  }catch(e:CancellationException){save();throw e}catch(e:Exception){if(e is ForumHttpException&&e.status in listOf(400,401,403,404,409,413,422,429))publicationPending=false;save();status=friendlyError(e)+"\nהטיוטה נשמרה. אם החיבור נותק בזמן השליחה, בדקו בדיון לפני ניסיון נוסף."}finally{busy=false;vm.sending=false}}
 }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
  Paper{
   Text(if(edit)"עריכת פוסט"else if(reply)"כתיבת תגובה"else "נושא חדש",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   Text("הטיוטה נשמרת אוטומטית",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=8.dp))
   if(publicationPending&&!busy)Text("לא התקבל אישור לסיום השליחה הקודמת. בדקו אם התוכן כבר פורסם בפורום לפני שליחה נוספת.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
   if(!reply){OutlinedButton(enabled=!busy,onClick={menu=true},modifier=Modifier.fillMaxWidth()){Text(all.firstOrNull{it.id==cid}?.name?:if(cid>0)"קטגוריה $cid"else "בחירת קטגוריה")};OutlinedTextField(value=title,onValueChange={title=it},label={Text("כותרת הדיון")},modifier=Modifier.fillMaxWidth(),enabled=!busy)}
   if(loadingOriginal)CircularProgressIndicator(Modifier.size(24.dp))
   if(originalFailed)OutlinedButton(onClick={rawRevision++}){Text("ניסיון נוסף לטעינת הפוסט")}
   Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(Triple("מודגש","**","**"),Triple("נטוי","*","*"),Triple("מחיקה","~~","~~"),Triple("כותרת","\n## ",""),Triple("ציטוט","\n> ",""),Triple("רשימה","\n- ",""),Triple("קוד","\n```\n","\n```"),Triple("קישור","[","](https://mitmachim.top)")).forEach{(label,before,after)->FilterChip(selected=false,enabled=!busy&&!loadingOriginal&&!originalFailed,onClick={wrap(before,after)},label={Text(label)})};FilterChip(selected=false,enabled=!busy&&!loadingOriginal&&!originalFailed,onClick={insertSpoiler()},label={Text("ספוילר")})}
   MentionSuggestions(vm,text,!busy&&!loadingOriginal&&!originalFailed){text=it;save()}
   OutlinedTextField(value=text,onValueChange={text=it},label={Text("תוכן הפוסט")},minLines=8,modifier=Modifier.fillMaxWidth(),enabled=!busy&&!loadingOriginal&&!originalFailed)
   Text("${number(text.text.length)} תווים",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=8.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(enabled=!busy&&!loadingOriginal&&!originalFailed,onClick={previewFocus.clearFocus();preview=true}){Icon(AppIcons.Visibility,null);Text(" תצוגת הפוסט")};OutlinedButton(enabled=!busy&&!loadingOriginal&&!originalFailed,onClick={picker.launch(arrayOf("image/png","image/jpeg","image/webp","image/gif","application/pdf"))}){Icon(AppIcons.AttachFile,null);Text(" צירוף קובץ")}}
   attachments.forEach{file->Row(verticalAlignment=Alignment.CenterVertically){Text(file.name+if(file.uploadedUrl!=null)" · הועלה"else "",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);IconButton(enabled=!busy,onClick={attachments=attachments-file;if(file.uploadedUrl!=null)text=TextFieldValue(text.text.replace(attachmentMarkdown(file.name,file.type,file.uploadedUrl),""));save();vm.deleteAttachment(file.uri)}){Icon(AppIcons.Close,"הסרת ${file.name}")}}}
  }
  if(busy)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
  Button(onClick={send()},enabled=!busy&&!loadingOriginal&&!originalFailed,modifier=Modifier.fillMaxWidth()){if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Text(if(edit)"שמירת השינויים"else if(reply)"שליחת תגובה"else "פרסום נושא")}
  if(status.isNotEmpty())Paper{Text(status,style=MaterialTheme.typography.bodyMedium)}
 }
 if(preview)PostPreviewSheet(vm,text.text,title,files=attachments){preview=false}
 if(menu)AlertDialog(onDismissRequest={menu=false},title={Text("בחירת קטגוריה")},text={Column{OutlinedTextField(value=categoryQuery,onValueChange={categoryQuery=it},label={Text("חיפוש קטגוריה")});Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())){all.filter{it.link.isBlank()&&it.name.contains(categoryQuery,true)}.forEach{c->FilledTonalButton(onClick={cid=c.id;menu=false},modifier=Modifier.fillMaxWidth()){Text(c.name)}}};if(all.isEmpty())OutlinedButton(onClick={vm.reloadDirectory()}){Text("טעינת קטגוריות מחדש")}}},confirmButton={OutlinedButton(onClick={menu=false}){Text("סגירה")}})
}
