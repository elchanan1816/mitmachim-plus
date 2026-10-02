package community.mitmachim.nativeapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.json.JSONObject

@Composable fun QuickReplyComposer(vm:AppModel,topic:Topic,request:Post?,requestId:Int,chat:Boolean=false,onRequestConsumed:()->Unit={}){
 val key=quickReplyDraftKey(vm.account.uid,topic.id)
 val restored=remember(key){runCatching{JSONObject(vm.prefs.getString(key,"{}")?:"{}")} .getOrDefault(JSONObject())}
 var files by remember(key){mutableStateOf(restored.array("files"))}
 var text by remember(key){mutableStateOf(restored.optString("content"))}
 var selection by remember(key){mutableStateOf(androidx.compose.ui.text.TextRange(text.length))}
 var replyTo by remember(key){mutableIntStateOf(restored.optInt("toPid"))}
 var replyName by remember(key){mutableStateOf(restored.optString("replyName"))}
 var replyPreview by remember(key){mutableStateOf(restored.optString("replyPreview"))}
 var publicationPending by remember(key){mutableStateOf(restored.optBoolean("submissionPending"))}
 var busy by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("")}
 var options by remember{mutableStateOf(false)}
 var preview by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 val focus=LocalFocusManager.current
 val requester=remember{FocusRequester()}
 fun save(){
  if(text.isNotBlank()||replyTo>0||files.length()>0){vm.saveDraft(key,JSONObject().put("title","").put("content",text).put("cid",topic.cid).put("tid",topic.id)
   .put("toPid",replyTo).put("replyName",replyName).put("replyPreview",replyPreview).put("mode","editor")
   .put("submissionPending",publicationPending).put("files",files))}
  else if(vm.prefs.contains(key))vm.deleteDraft(key)
 }
 val saveLatest by rememberUpdatedState({save()})
 LaunchedEffect(key){
  // The full editor flushes its latest draft on disposal. Read after that commit, not during composition.
  yield()
  val fresh=runCatching{JSONObject(vm.prefs.getString(key,"{}")?:"{}")}.getOrDefault(JSONObject())
  text=fresh.optString("content");files=fresh.array("files");publicationPending=fresh.optBoolean("submissionPending")
  replyTo=fresh.optInt("toPid");replyName=fresh.optString("replyName");replyPreview=fresh.optString("replyPreview")
 }
 LaunchedEffect(text,replyTo,publicationPending){delay(400);save()}
 DisposableEffect(key){onDispose{saveLatest()}}
 LaunchedEffect(requestId){if(requestId>0&&request!=null){replyTo=request.id;replyName=request.author;replyPreview=threadSearchText(request).take(130);vm.quickReplyOpen=true;save();onRequestConsumed()}}
 LaunchedEffect(vm.quickReplyOpen){if(vm.quickReplyOpen)requester.requestFocus()}
 BackHandler(vm.quickReplyOpen&&!busy){focus.clearFocus();vm.quickReplyOpen=false}
 fun fullEditor(){save();focus.clearFocus();vm.open(Route("editor",cid=topic.cid,tid=topic.id,pid=replyTo,draft=key))}
 fun send(){
  if(busy||publicationPending)return
  // Attachments added in the full editor must not disappear or be silently omitted from a quick send.
  if(files.length()>0){fullEditor();return}
  val invalid=DraftRules.validate("",text,topic.cid,true,vm.api.minTitle,vm.api.minPost,vm.api.maxPost)
  if(invalid!=null){status=invalid;return}
  save()
  if(vm.account.uid<=0){focus.clearFocus();vm.open(Route("login"));vm.showMessage("התגובה נשמרה. היכנסו לחשבון כדי לשלוח אותה.");return}
  if(vm.demo){status="שליחה חסומה בסביבת הבדיקה המקומית";return}
  busy=true;vm.sending=true;publicationPending=true;save()
  scope.launch{
   try{
    val result=vm.api.publish(topic.cid,"",text,topic.id,replyTo)
    text="";replyTo=0;replyName="";replyPreview="";publicationPending=false;vm.deleteDraft(key)
    focus.clearFocus();vm.quickReplyOpen=false;vm.sending=false
    vm.completeQuickReply(result)
   }catch(e:CancellationException){save();throw e}catch(e:Exception){
    if(e is ForumHttpException&&e.status in listOf(400,401,403,404,409,413,422,429))publicationPending=false
    save();status=friendlyError(e)
   }finally{busy=false;vm.sending=false}
  }
 }
 Surface(color=MaterialTheme.colorScheme.surface){
  Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
   if(!vm.quickReplyOpen){
    Row(verticalAlignment=Alignment.CenterVertically){
     Surface(onClick={vm.quickReplyOpen=true},shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.weight(1f).testTag("quick-reply-open")){
      Row(Modifier.padding(horizontal=14.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically){
       Text(if(text.isBlank())if(chat)"הודעה…"else "כתיבת תגובה…"else "המשך הטיוטה",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
       Icon(AppIcons.Edit,null,Modifier.size(18.dp))
      }
     }
     IconButton(onClick={fullEditor()}){Icon(AppIcons.AttachFile,"פתיחת העורך המלא וצירוף קבצים")}
    }
   }else{
    if(replyTo>0)Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f)){
     Row(Modifier.fillMaxWidth().padding(start=10.dp),verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f).padding(vertical=5.dp)){
       Text("בתגובה ל־${replyName.ifBlank{"פוסט קודם"}}",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelSmall)
       Text(replyPreview,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
      }
      IconButton(enabled=!busy,onClick={replyTo=0;replyName="";replyPreview="";save()}){Icon(AppIcons.Close,"ביטול תגובה לפוסט",Modifier.size(17.dp))}
     }
    }
    val input=androidx.compose.ui.text.input.TextFieldValue(text,selection)
    MentionSuggestions(vm,input,!busy){text=it.text;selection=it.selection;save()}
    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(5.dp)){
     OutlinedTextField(value=input,onValueChange={text=it.text;selection=it.selection},placeholder={Text(if(chat)"הודעה…"else "כתיבת תגובה…")},enabled=!busy,minLines=1,maxLines=4,
      textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=androidx.compose.ui.text.style.TextDirection.Content),
      shape=RoundedCornerShape(26.dp),modifier=Modifier.weight(1f).focusRequester(requester).testTag("quick-reply-input"),
      trailingIcon={CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp){
       Row{
        IconButton(enabled=!busy,onClick={fullEditor()},modifier=Modifier.size(30.dp)){Icon(AppIcons.AttachFile,"פתיחת העורך המלא וצירוף קבצים",Modifier.size(19.dp))}
        Box{
         IconButton(enabled=!busy,onClick={options=true},modifier=Modifier.size(30.dp)){Icon(AppIcons.More,"אפשרויות הכתיבה",Modifier.size(18.dp))}
         DropdownMenu(expanded=options,onDismissRequest={options=false}){
          DropdownMenuItem(text={Text("תצוגה לפני שליחה")},leadingIcon={Icon(AppIcons.Visibility,null)},enabled=text.isNotBlank()||files.length()>0,onClick={options=false;save();focus.clearFocus();preview=true})
          DropdownMenuItem(text={Text("מזעור אזור הכתיבה")},onClick={options=false;focus.clearFocus();vm.quickReplyOpen=false})
         }
        }
       }
      }})
     FilledIconButton(enabled=!busy&&!publicationPending&&text.isNotBlank(),onClick={send()},modifier=Modifier.size(46.dp)){
      if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Icon(AppIcons.Send,"שליחת תגובה")
     }
    }
   }
   if(files.length()>0&&vm.quickReplyOpen)Text("קבצים מצורפים — המשך ושליחה בעורך המלא",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
   if(publicationPending&&!busy){
    Text("לא התקבל אישור לשליחה. בדקו בדיון אם התגובה פורסמה לפני ניסיון נוסף. הטיוטה נשמרה.",style=MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick={publicationPending=false;status="";save()}){Text("בדקתי בדיון — אפשר לנסות שוב")}
   }
  }
 }
 if(preview)PostPreviewSheet(vm,text,replyName=replyName,files=restoreFiles(files)){preview=false}
}
