package community.mitmachim.nativeapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ChatRoom(val id:Int,val title:String,val teaser:String,val unread:Boolean)
data class ChatMessage(val id:Int,val author:String,val html:String,val time:Long,val deleted:Boolean)
fun parseRooms(value:Any?):List<ChatRoom> {
 val rows=when(value){is JSONArray->value;is JSONObject->when{value.has("rooms")->value.array("rooms");value.optInt("roomId")>0->JSONArray().put(value);else->throw IllegalArgumentException("תשובת רשימת השיחות אינה מוכרת")};else->throw IllegalArgumentException("לא התקבלה רשימת שיחות")}
 return rows.objects().map{r->ChatRoom(r.optInt("roomId"),plain(r.optString("roomName")).ifBlank{plain(r.optString("usernames")).ifBlank{r.array("users").objects().joinToString(", "){plain(it.optString("displayname",it.optString("username")))}}}.ifBlank{"שיחה ${r.optInt("roomId")}"},plain(r.obj("teaser").optString("content")),r.flag("unread"))}.filter{it.id>0}
}
fun parseMessages(rows:JSONArray)=rows.objects().map{m->ChatMessage(m.optInt("messageId",m.optInt("mid")),plain(m.obj("fromUser").optString("displayname",m.obj("fromUser").optString("username","חבר קהילה"))),m.optString("content"),m.optLong("timestamp"),m.flag("deleted"))}.filter{it.id>0}.sortedBy{it.time}


@Composable fun ChatScreen(vm:AppModel,onLink:(String)->Unit){
 val room=vm.route.tid;val scope=rememberCoroutineScope()
 var refresh by remember{mutableIntStateOf(0)};var start by rememberSaveable{mutableIntStateOf(0)}
 var rooms by remember{mutableStateOf<Load<List<ChatRoom>>>(Load.Waiting)};var messages by remember{mutableStateOf<Load<List<ChatMessage>>>(Load.Waiting)}
 val draftKey="chat-draft-${vm.account.uid}-$room"
 var message by rememberSaveable(draftKey){mutableStateOf(vm.prefs.getString(draftKey,"").orEmpty())}
 var busy by remember{mutableStateOf(false)};var status by remember{mutableStateOf("")}
 var recipient by rememberSaveable{mutableStateOf("")};var matches by remember{mutableStateOf(emptyList<Account>())};var searching by remember{mutableStateOf(false)}
 val previous=remember{mutableStateListOf<Int>()}
 fun create(uid:Int){if(vm.demo||busy)return;busy=true;scope.launch{try{val id=vm.api.newChat(uid);vm.open(Route("chat",tid=id))}catch(e:Exception){status=friendlyError(e)}finally{busy=false}}}
 LaunchedEffect(room,start,refresh,vm.account.uid){if(vm.account.uid>0&&!vm.demo){try{if(room==0){rooms=Load.Waiting;rooms=Load.Ready(vm.api.roomList(start))}else{messages=Load.Waiting;messages=Load.Ready(vm.api.messages(room,start))}}catch(e:Exception){val error=friendlyError(e);if(room==0)rooms=Load.Failed(error)else messages=Load.Failed(error)}}}
 if(vm.account.uid==0){Column(Modifier.padding(18.dp)){EmptyCard("הודעות פרטיות","היכנסו לחשבון כדי לקרוא ולשלוח הודעות.",AppIcons.Forum);Button(onClick={vm.open(Route("login"))}){Text("כניסה לחשבון")}};return}
 LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{SectionTitle(if(room==0)"השיחות שלי"else "שיחה פרטית","רענון"){refresh++}}
  if(room==0){
   item{Paper{Text("שיחה חדשה",fontWeight=FontWeight.Bold);OutlinedTextField(value=recipient,onValueChange={recipient=it;matches=emptyList()},label={Text("חיפוש לפי שם משתמש")},singleLine=true,modifier=Modifier.fillMaxWidth())
    OutlinedButton(enabled=recipient.trim().length>=2&&!searching&&!busy,onClick={searching=true;scope.launch{try{matches=vm.api.users(recipient.trim()).filter{it.uid!=vm.account.uid};status=if(matches.isEmpty())"לא נמצאו משתמשים בשם הזה"else ""}catch(e:Exception){status=friendlyError(e)}finally{searching=false}}}){Text(if(searching)"מחפש…"else "חיפוש משתמש")}
    matches.take(15).forEach{user->FilledTonalButton(enabled=!busy,onClick={create(user.uid)}){Text("שיחה עם ${user.name}")}}
    recipient.toIntOrNull()?.takeIf{it>0&&it!=vm.account.uid}?.let{uid->OutlinedButton(enabled=!busy,onClick={create(uid)}){Text("פתיחת שיחה לפי מזהה $uid")}}
   }}
   when(val data=rooms){Load.Waiting->item{Skeletons()};is Load.Failed->item{ErrorCard(data.message){refresh++}};is Load.Ready->{if(data.value.isEmpty())item{EmptyCard("אין שיחות להצגה","אפשר לפתוח שיחה חדשה עם חבר בפורום.",AppIcons.Forum)};items(data.value,key={it.id}){r->Paper(Modifier.clickable{vm.open(Route("chat",tid=r.id))}){if(r.unread)Badge("לא נקרא");Text(r.title,fontWeight=FontWeight.Bold);Text(r.teaser,maxLines=3,style=MaterialTheme.typography.bodySmall)}}}}
  }else{
   when(val data=messages){Load.Waiting->item{Skeletons()};is Load.Failed->item{ErrorCard(data.message){refresh++}};is Load.Ready->{if(data.value.isEmpty())item{Text("אין הודעות בטווח הזה")};items(data.value,key={it.id}){m->Paper{Text(m.author,fontWeight=FontWeight.Bold);Text(ago(m.time),style=MaterialTheme.typography.labelSmall);Spacer(Modifier.height(8.dp));if(m.deleted)Text("ההודעה נמחקה")else RichContent(m.html,vm,onLink)}}}}
   item{Paper{OutlinedTextField(value=message,onValueChange={message=it;vm.prefs.edit().putString(draftKey,it).apply()},label={Text("כתיבת הודעה")},modifier=Modifier.fillMaxWidth(),enabled=!busy,minLines=2)
    Button(enabled=message.isNotBlank()&&!busy,onClick={if(message.length>10000)status="ההודעה ארוכה מדי. קצרו אותה ונסו שוב."else if(!vm.demo){busy=true;scope.launch{try{vm.api.sendMessage(room,message);message="";vm.prefs.edit().remove(draftKey).apply();status="ההודעה נשלחה";start=0;previous.clear();refresh++}catch(e:Exception){status=friendlyError(e)+"\nהטקסט נשמר. בדקו אם ההודעה נקלטה לפני שליחה חוזרת."}finally{busy=false}}}}){Text(if(busy)"שולח…"else "שליחה")}}}
  }
  if(status.isNotBlank())item{Paper{Text(status,style=MaterialTheme.typography.bodyMedium)}}
  item{val count=if(room==0)(rooms as? Load.Ready)?.value?.size?:0 else (messages as? Load.Ready)?.value?.size?:0;Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton(enabled=start>0,onClick={start=if(previous.isNotEmpty())previous.removeAt(previous.lastIndex)else 0}){Text(if(room==0)"הקודם"else "הודעות חדשות יותר")};OutlinedButton(enabled=count>0,onClick={previous.add(start);start+=count}){Text(if(room==0)"הבא"else "הודעות קודמות")}}}
 }
}
