package community.mitmachim.nativeapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 0.15's visual hierarchy, without restoring its old networking/navigation code. */
@Composable fun DiscussionHeading(data:ThreadPage,narrow:Boolean,onDetails:()->Unit){
 Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal=20.dp,vertical=18.dp).testTag("discussion-heading")){
  Box(Modifier.width(42.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
  Spacer(Modifier.height(13.dp));Badge(data.topic.category.ifBlank{"דיון בקהילה"})
  Text(data.topic.title,Modifier.clickable(onClick=onDetails).padding(top=12.dp,bottom=8.dp),color=MaterialTheme.colorScheme.onSurface,
   style=MaterialTheme.typography.headlineMedium.copy(fontSize=if(narrow)25.sp else 28.sp,lineHeight=if(narrow)31.sp else 35.sp),fontWeight=FontWeight.Bold)
  Text("${number(data.topic.posts)} פוסטים · ${number(data.topic.views)} צפיות",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
  if(data.locked||data.topic.solved)Row(Modifier.padding(top=9.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   if(data.locked)Badge("נעול");if(data.topic.solved)Badge("נפתר")
  }
 }
}
@Composable fun DiscussionTools(vm:AppModel,data:ThreadPage,readAtOpen:Int,onRemember:()->Unit,onSource:(String)->Unit){
 FlowRow(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp).testTag("discussion-tools"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
  FilledTonalButton(onClick={vm.toggleSaved(data.topic)}){Icon(if(vm.saved.any{it.id==data.topic.id})AppIcons.Bookmark else AppIcons.BookmarkBorder,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text(if(vm.saved.any{it.id==data.topic.id})"נשמר"else "שמירה")}
  FollowActions(vm,data.topic.id,data.following)
  if(readAtOpen>0&&readAtOpen<data.topic.posts)OutlinedButton(enabled=!vm.sending,onClick={onRemember();vm.navigateThread(index=readAtOpen+1,rememberReturn=true)}){Text("הראשון שלא נקרא")}
  if(vm.account.uid>0)OutlinedButton(onClick={vm.markCurrentThreadRead()}){Text("סימון כנקרא")}
  OutlinedButton(onClick={onSource("$FORUM/topic/${data.topic.id}?page=${vm.threadVisiblePage}")}){Text("באתר")}
 }
}
@Composable fun DiscussionPostAuthor(vm:AppModel,post:Post){
 val context=LocalContext.current
 val profile=Modifier.clickable(enabled=post.authorSlug.isNotBlank()){vm.open(Route("user",query=post.authorSlug))}
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
  UserAvatar(post.avatar,post.author,40.dp,profile,online=post.online,uid=post.uid)
  Spacer(Modifier.width(10.dp))
  Column(Modifier.weight(1f).then(profile)){
   Text(post.author,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
   Text(ago(post.time),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  Surface(onClick={
   (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("קישור לפוסט","$FORUM/post/${post.id}"));vm.showMessage("הקישור לפוסט הועתק")
  },shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainer){Text("#${post.index}",Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium)}
 }
}
