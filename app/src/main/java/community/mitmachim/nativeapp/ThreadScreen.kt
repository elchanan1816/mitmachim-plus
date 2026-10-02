package community.mitmachim.nativeapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

fun threadBoundary(route:Route,pages:Int,end:Boolean)=route.copy(page=if(end)pages.coerceAtLeast(1)else 1,end=end,index=if(end)0 else 1,pid=0)
fun quotePost(post:Post)="[בתגובה ל־${post.author}]($FORUM/post/${post.id})\n"+plain(post.html).lines().joinToString("\n"){"> $it"}+"\n\n"
@Composable fun ThreadLoading(){
 val transition=rememberInfiniteTransition(label="thread-loading")
 val alpha by transition.animateFloat(.18f,.44f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="thread-pulse")
 val tint=MaterialTheme.colorScheme.onSurface.copy(alpha=alpha)
 Column(Modifier.fillMaxWidth().semantics{contentDescription="טוענים את הדיון"}){
  Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp)){
   Box(Modifier.fillMaxWidth(.38f).height(18.dp).clip(CircleShape).background(tint));Spacer(Modifier.height(16.dp))
   Box(Modifier.fillMaxWidth(.9f).height(26.dp).clip(CircleShape).background(tint));Spacer(Modifier.height(8.dp))
   Box(Modifier.fillMaxWidth(.7f).height(26.dp).clip(CircleShape).background(tint))
  }
  repeat(3){Column(Modifier.fillMaxWidth().padding(start=18.dp,end=34.dp,top=20.dp,bottom=18.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(40.dp).clip(CircleShape).background(tint));Spacer(Modifier.width(10.dp));Box(Modifier.width(130.dp).height(16.dp).clip(CircleShape).background(tint))}
   Spacer(Modifier.height(22.dp));Box(Modifier.fillMaxWidth().height(15.dp).clip(CircleShape).background(tint));Spacer(Modifier.height(9.dp));Box(Modifier.fillMaxWidth(.76f).height(15.dp).clip(CircleShape).background(tint))
  };HorizontalDivider()}
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PostActions(vm:AppModel,post:Post,topic:Topic,canReply:Boolean,onShare:()->Unit,onReply:(()->Unit)?=null,onReplies:(()->Unit)?=null,compact:Boolean=false){
 var direction by remember(post.id,post.voted,post.downvoted){mutableIntStateOf(if(post.voted)1 else if(post.downvoted)-1 else 0)}
 var count by remember(post.id,post.votes){mutableIntStateOf(post.votes)}
 var busy by remember(post.id){mutableStateOf(false)}
 var confirmDelete by remember(post.id){mutableStateOf(false)}
 var reporting by remember(post.id){mutableStateOf(false)}
 var reportReason by remember(post.id){mutableStateOf("")}
 var menu by remember(post.id){mutableStateOf(false)}
 var bookmarked by remember(post.id,post.bookmarked){mutableStateOf(post.bookmarked)}
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 fun vote(value:Int){
  if(vm.account.uid<=0){vm.open(Route("login"));return}
  if(vm.demo){vm.showMessage("הצבעה אינה זמינה במצב בדיקה");return}
  val next=if(direction==value)0 else value
  busy=true
  scope.launch{try{vm.api.vote(post.id,next);count+=next-direction;direction=next}catch(e:Exception){vm.showMessage(friendlyError(e))}finally{busy=false}}
 }
 FlowRow(Modifier.then(if(compact)Modifier else Modifier.fillMaxWidth().testTag("discussion-actions-${post.id}")),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
  if(!compact){
   if(canReply)FilledTonalButton(enabled=!vm.sending,onClick={onReply?.invoke()?:vm.open(Route("editor",cid=topic.cid,tid=topic.id,pid=post.id))},contentPadding=PaddingValues(horizontal=12.dp,vertical=7.dp)){
    Icon(AppIcons.Reply,"תגובה לפוסט #${post.index}",Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("תגובה")
   }
   OutlinedButton(enabled=!busy,onClick={vote(1)},contentPadding=PaddingValues(horizontal=10.dp,vertical=7.dp)){
    Icon(AppIcons.ThumbUp,if(direction==1)"ביטול לייק"else "לייק",Modifier.size(17.dp));Text(" ${number(count)}")
   }
   IconButton(enabled=!busy,onClick={vote(-1)},modifier=Modifier.size(42.dp)){Icon(AppIcons.ThumbDown,if(direction == -1)"ביטול דיסלייק"else "דיסלייק",Modifier.size(19.dp),tint=if(direction == -1)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
  }
  CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp){
   IconButton(enabled=!busy,onClick={menu=true},modifier=Modifier.size(if(compact)28.dp else 32.dp)){Icon(AppIcons.More,"פעולות לפוסט #${post.index}",Modifier.size(16.dp))}
  }
 }
 if(menu)ModalBottomSheet(onDismissRequest={menu=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
  Column(Modifier.fillMaxWidth().heightIn(max=640.dp).verticalScroll(rememberScrollState()).padding(horizontal=12.dp).padding(bottom=24.dp)){
    Text("פוסט #${post.index} · ${post.author}",Modifier.padding(horizontal=12.dp,vertical=8.dp),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
    ActionGroupLabel("תגובה והצבעה")
    if(canReply)PostSheetItem(text={Text("תגובה לפוסט")},leadingIcon={Icon(AppIcons.Reply,null)},onClick={menu=false;onReply?.invoke()?:vm.open(Route("editor",cid=topic.cid,tid=topic.id,pid=post.id))})
    PostSheetItem(text={Text(if(direction==1)"ביטול לייק · ${number(count)}"else "לייק · ${number(count)}")},enabled=!busy,leadingIcon={Icon(AppIcons.ThumbUp,null)},onClick={menu=false;vote(1)})
    PostSheetItem(text={Text(if(direction == -1)"ביטול דיסלייק"else "דיסלייק")},enabled=!busy,leadingIcon={Icon(AppIcons.ThumbDown,null)},onClick={menu=false;vote(-1)})
    if(onReplies!=null)PostSheetItem(text={Text("הצגת תגובות לפוסט")},onClick={menu=false;onReplies()})
    if(canReply)PostSheetItem(text={Text("ציטוט הפוסט")},onClick={menu=false;vm.open(Route("editor",cid=topic.cid,tid=topic.id,pid=post.id,query=quotePost(post)))})
    HorizontalDivider(Modifier.padding(vertical=6.dp));ActionGroupLabel("שיתוף ושמירה")
    PostSheetItem(text={Text("שיתוף קישור")},onClick={menu=false;onShare()})
    PostSheetItem(text={Text("העתקת קישור לפוסט")},onClick={menu=false;(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("קישור לפוסט","$FORUM/post/${post.id}"));vm.showMessage("הקישור לפוסט הועתק")})
    if(post.authorSlug.isNotBlank())PostSheetItem(text={Text("פרופיל הכותב")},onClick={menu=false;vm.open(Route("user",query=post.authorSlug))})
    if(vm.account.uid>0)PostSheetItem(text={Text(if(bookmarked)"הסרה מסימניות הפורום"else "שמירת הפוסט בסימניות הפורום")},onClick={menu=false;busy=true;scope.launch{try{vm.api.bookmarkPost(post.id,bookmarked);bookmarked=!bookmarked;vm.showMessage(if(bookmarked)"הפוסט נשמר בפורום"else "הפוסט הוסר מסימניות הפורום")}catch(e:Exception){vm.showMessage(friendlyError(e))}finally{busy=false}}})
    if(post.canEdit||post.canDelete||vm.account.uid>0&&post.uid!=vm.account.uid){HorizontalDivider(Modifier.padding(vertical=6.dp));ActionGroupLabel("ניהול")}
    if(vm.account.uid>0&&post.uid!=vm.account.uid)PostSheetItem(text={Text("דיווח לצוות הפורום")},onClick={menu=false;reporting=true})
    if(post.canEdit)PostSheetItem(text={Text("עריכת הפוסט")},onClick={menu=false;vm.open(Route("edit",cid=topic.cid,tid=topic.id,pid=post.id,index=post.index))})
    if(post.canDelete)PostSheetItem(text={Text("מחיקת הפוסט")},onClick={menu=false;confirmDelete=true})
   }
 }
 if(reporting)AlertDialog(onDismissRequest={if(!busy)reporting=false},title={Text("דיווח על פוסט")},text={Column{Text("הסבירו בקצרה מה הבעיה. הדיווח יישלח לצוות הפורום.");Spacer(Modifier.height(12.dp));OutlinedTextField(value=reportReason,onValueChange={reportReason=it.take(500)},label={Text("סיבת הדיווח")},minLines=2,maxLines=4,modifier=Modifier.fillMaxWidth())}},confirmButton={Button(enabled=!busy&&reportReason.trim().length>=5,onClick={busy=true;scope.launch{try{vm.api.reportPost(post.id,reportReason);reporting=false;reportReason="";vm.showMessage("הדיווח נשלח לצוות הפורום")}catch(e:Exception){vm.showMessage(friendlyError(e))}finally{busy=false}}}){Text(if(busy)"שולח…"else "שליחת דיווח")}},dismissButton={OutlinedButton(enabled=!busy,onClick={reporting=false}){Text("ביטול")}})
 if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("למחוק את הפוסט?")},text={Text("הפוסט יוסתר בדיון בפורום.")},confirmButton={Button(enabled=!busy,onClick={
  confirmDelete=false;busy=true
  scope.launch{try{vm.api.deletePost(post.id);vm.showMessage("הפוסט נמחק");vm.retry()}catch(e:Exception){vm.showMessage(friendlyError(e))}finally{busy=false}}
 }){Text("מחיקה")}},dismissButton={OutlinedButton(onClick={confirmDelete=false}){Text("ביטול")}})
}
@Composable private fun ActionGroupLabel(value:String){Text(value,Modifier.padding(horizontal=12.dp,vertical=7.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)}
@Composable fun FollowActions(vm:AppModel,tid:Int,initial:Boolean){
 var following by remember(tid,initial){mutableStateOf(initial)};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 FilledTonalButton(enabled=!busy,onClick={if(vm.account.uid<=0)vm.open(Route("login"))else if(!vm.demo){busy=true;scope.launch{try{vm.api.follow(tid,following);following=!following;vm.showMessage(if(following)"המעקב אחר הדיון הופעל"else "המעקב הופסק")}catch(e:Exception){vm.showMessage(friendlyError(e))}finally{busy=false}}}}){Text(if(busy)"מעדכן…"else if(following)"במעקב"else "מעקב")}
}
@Composable private fun PostSheetItem(text:@Composable ()->Unit,onClick:()->Unit,enabled:Boolean=true,leadingIcon:(@Composable ()->Unit)?=null){
 DropdownMenuItem(text=text,onClick=onClick,enabled=enabled,leadingIcon=leadingIcon,modifier=Modifier.fillMaxWidth())
}
