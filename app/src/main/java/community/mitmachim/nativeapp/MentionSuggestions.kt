package community.mitmachim.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class MentionQuery(val start:Int,val end:Int,val query:String)
fun mentionQuery(value:TextFieldValue):MentionQuery? {
 if(!value.selection.collapsed)return null
 val end=value.selection.end.coerceIn(0,value.text.length)
 val prefix=value.text.substring(0,end)
 val match=Regex("(?:^|[\\s(])@([^\\s@]{1,40})$").find(prefix)?:return null
 return MentionQuery(prefix.lastIndexOf('@'),end,match.groupValues[1])
}
fun insertMention(value:TextFieldValue,query:MentionQuery,slug:String):TextFieldValue {
 if(slug.isBlank()||slug.any{it.isWhitespace()||it=='@'}||mentionQuery(value)!=query)return value
 val inserted="@$slug "
 return TextFieldValue(value.text.replaceRange(query.start,query.end,inserted),TextRange(query.start+inserted.length))
}

@Composable fun MentionSuggestions(vm:AppModel,value:TextFieldValue,enabled:Boolean,onChoose:(TextFieldValue)->Unit){
 val query=if(enabled)mentionQuery(value)else null
 var matches by remember{mutableStateOf(emptyList<Account>())}
 var status by remember{mutableStateOf("")}
 LaunchedEffect(query){
  matches=emptyList();status=""
  if(query!=null){
   status="מחפש משתמשים…";delay(300)
   try{
    matches=if(vm.demo)emptyList()else vm.api.users(query.query).filter{it.slug.isNotBlank()&&!it.slug.any(Char::isWhitespace)}.take(6)
    status=if(matches.isEmpty())"לא נמצאו משתמשים"else ""
   }catch(e:CancellationException){throw e}catch(_:Exception){status="לא ניתן לטעון הצעות תיוג כרגע"}
  }
 }
 if(query!=null)Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.fillMaxWidth()){
  Column(Modifier.heightIn(max=144.dp).verticalScroll(rememberScrollState())){
   if(status.isNotBlank())Text(status,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
   matches.forEach{user->TextButton(onClick={onChoose(insertMention(value,query,user.slug))},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
    Text("${user.name} · @${user.slug}",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
   }}
  }
 }
}
