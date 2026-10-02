package community.mitmachim.nativeapp

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun UserAvatar(url:String,name:String,size:Dp,modifier:Modifier=Modifier,online:Boolean=false,uid:Int=0) {
 var failed by remember(url){mutableStateOf(false)}
 Box(modifier.size(size),contentAlignment=Alignment.Center){
  Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(avatarSwatches[avatarColorIndex(name,uid)])),contentAlignment=Alignment.Center){
   Text(avatarInitial(name),color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=(size.value*.42f).coerceIn(12f,27f).sp)
   if(url.isNotBlank()&&!failed)AsyncImage(model=url,contentDescription="תמונת הפרופיל של $name",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize().clip(CircleShape),onError={failed=true})
  }
  if(online)Box(Modifier.align(Alignment.BottomEnd).size((size.value*.26f).coerceIn(9f,17f).dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(2.dp).clip(CircleShape).background(Color(0xFF2FAE67)).semantics { contentDescription="מחובר כעת" })
 }
}

@Composable fun UserProfileScreen(vm:AppModel,onLink:(String)->Unit) {
 val slug=vm.route.query
 val scope=rememberCoroutineScope()
 var revision by remember(slug){mutableIntStateOf(0)}
 var data by remember(slug){mutableStateOf<Load<PublicProfile>>(Load.Waiting)}
 var section by remember(slug){mutableStateOf("about")}
 var page by remember(slug){mutableIntStateOf(1)}
 var activity by remember(slug){mutableStateOf<Load<ProfileSection>>(Load.Waiting)}
 LaunchedEffect(slug,revision) {
  data=Load.Waiting
  data=try{Load.Ready(vm.api.profile(slug))}catch(e:Exception){Load.Failed(friendlyError(e))}
 }
 LaunchedEffect(slug,section,page,revision) {
  if(section!="about") {
   activity=Load.Waiting
   activity=try{Load.Ready(vm.api.profileSection(slug,section,page))}catch(e:Exception){Load.Failed(friendlyError(e))}
  }
 }
 val tabs=listOf("about" to "אודות","topics" to "נושאים","posts" to "פוסטים","best" to "מובילים","controversial" to "שנויים במחלוקת","followers" to "עוקבים","following" to "נעקבים","groups" to "קבוצות","shares" to "שיתופים")
 LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  when(val value=data) {
   Load.Waiting->item { Skeletons() }
   is Load.Failed->item { ErrorCard(value.message){revision++} }
   is Load.Ready-> {
    val user=value.value
    item { Paper {
     Row(verticalAlignment=Alignment.CenterVertically) {
      UserAvatar(user.avatar,user.name,72.dp,online=user.online,uid=user.uid)
      Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {
       Text(user.name,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
       if(user.online)Text("מחובר כעת",color=Color(0xFF23864F),style=MaterialTheme.typography.labelMedium)
       if(user.group.isNotBlank())Text(user.group,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
      }
     }
     Spacer(Modifier.height(14.dp))
     Text("${number(user.topics)} נושאים  ·  ${number(user.posts)} פוסטים  ·  ${number(user.reputation)} מוניטין",style=MaterialTheme.typography.bodyMedium)
     if(vm.account.uid>0&&vm.account.uid!=user.uid&&!vm.demo){
      var following by remember(user.uid,user.isFollowing){mutableStateOf(user.isFollowing)}
      var busy by remember(user.uid){mutableStateOf(false)}
      FilledTonalButton(enabled=!busy,onClick={busy=true;scope.launch{try{vm.api.followUser(user.uid,following);following=!following;vm.showMessage(if(following)"המעקב אחר המשתמש הופעל"else "המעקב הופסק")}catch(e:Exception){vm.showMessage(friendlyError(e))}finally{busy=false}}},modifier=Modifier.padding(top=12.dp)){Text(if(following)"במעקב · הפסקה"else "מעקב אחר המשתמש")}
     }
    }}
    item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     tabs.forEach{(id,label)->FilterChip(selected=section==id,onClick={section=id;page=1},label={Text(label)})}
    }}
    if(section=="about")item { Paper {
     Text("על המשתמש",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
     if(user.about.isNotBlank())Text(user.about,Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyLarge)
     if(user.joined>0)Text("הצטרפות: ${SimpleDateFormat("dd.MM.yyyy",Locale.US).format(Date(user.joined))}",Modifier.padding(top=10.dp))
     if(user.lastOnline>0)Text("נראה לאחרונה: ${ago(user.lastOnline)}",Modifier.padding(top=6.dp))
     Text("${number(user.views)} צפיות בפרופיל · ${number(user.followers)} עוקבים · ${number(user.following)} נעקבים",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium)
     if(user.groups.isNotEmpty())Text("קבוצות: ${user.groups.joinToString(" · ")}",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium)
    }}
    if(section!="about")when(val listing=activity) {
     Load.Waiting->item { Skeletons() }
     is Load.Failed->item { ErrorCard(listing.message){revision++} }
     is Load.Ready->{val result=listing.value
      if(result.topics.isEmpty()&&result.posts.isEmpty()&&result.people.isEmpty()&&result.groups.isEmpty())item { EmptyCard("אין פריטים להצגה","לא נמצאו פריטים בלשונית הזו.") }
      items(result.topics.size) { index -> TopicCard(result.topics[index],vm) }
      items(result.posts.size) { index -> val post=result.posts[index];Card(onClick={vm.openPost(post.pid)},modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(post.title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall);if(post.excerpt.isNotBlank())Text(post.excerpt,Modifier.padding(top=8.dp),maxLines=4,style=MaterialTheme.typography.bodyMedium);Text("${ago(post.time)} · ${number(post.votes)} הצבעות",Modifier.padding(top=9.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) } } }
      items(result.people.size) { index -> val person=result.people[index];Card(onClick={vm.open(Route("user",query=person.slug))},modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) { UserAvatar(person.avatar,person.name,40.dp,online=person.online,uid=person.uid);Spacer(Modifier.width(12.dp));Text(person.name,fontWeight=FontWeight.SemiBold) } } }
      items(result.groups.size) { index -> Paper { Text(result.groups[index],fontWeight=FontWeight.SemiBold) } }
      item { Pager(result.page,result.pages){page=it} }
     }
    }
    item { OutlinedButton(onClick={onLink("$FORUM/user/${Uri.encode(user.slug)}")},modifier=Modifier.fillMaxWidth()){Text("פתיחת הפרופיל באתר")} }
   }
  }
 }
}
