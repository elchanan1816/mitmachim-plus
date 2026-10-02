package community.mitmachim.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

@Composable fun ThreadProgressRail(progress:Float,total:Int,onPreview:(Int?)->Unit,onJump:(Int)->Unit,enabled:Boolean=true,height:Dp=180.dp,width:Dp=32.dp){
 var preview by remember{mutableFloatStateOf(progress)}
 var dragging by remember{mutableStateOf(false)}
 val latestProgress by rememberUpdatedState(progress)
 val latestJump by rememberUpdatedState(onJump)
 val latestPreview by rememberUpdatedState(onPreview)
 LaunchedEffect(progress){if(!dragging)preview=progress}
 DisposableEffect(Unit){onDispose{latestPreview(null)}}
 BoxWithConstraints(Modifier.width(width).height(height).then(if(enabled)Modifier.pointerInput(total){
  detectTapGestures{position->latestJump(threadTargetIndex(position.y/size.height,total))}
 }.pointerInput(total){
  detectVerticalDragGestures(onDragStart={position->dragging=true;preview=(position.y/size.height).coerceIn(0f,1f);latestPreview(threadTargetIndex(preview,total))},
   onDragEnd={dragging=false;latestPreview(null);latestJump(threadTargetIndex(preview,total))},
   onDragCancel={dragging=false;preview=latestProgress;latestPreview(null)}){change,amount->
   change.consume();preview=(preview+amount/size.height).coerceIn(0f,1f);latestPreview(threadTargetIndex(preview,total))
  }
 }else Modifier).semantics{
  contentDescription="סרגל מיקום בדיון"
  stateDescription="פוסט ${threadTargetIndex(preview,total)} מתוך $total"
  progressBarRangeInfo=ProgressBarRangeInfo(preview,0f..1f)
  if(!enabled)disabled()
  setProgress{value->if(enabled){latestJump(threadTargetIndex(value,total));true}else false}
 },contentAlignment=Alignment.TopCenter){
  Box(Modifier.align(Alignment.Center).width(3.dp).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
  Box(Modifier.offset(y=(maxHeight-18.dp)*preview).size(10.dp,18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
 }
}

@Composable fun ThreadBottomPager(page:Int,pages:Int,busy:Boolean,onPrevious:()->Unit,onNext:()->Unit,onPosition:()->Unit){
 Surface(color=MaterialTheme.colorScheme.surface,tonalElevation=2.dp){
  Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=5.dp).testTag("thread-pager"),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick=onPrevious,enabled=!busy&&page>1){Icon(AppIcons.PreviousPage,"לעמוד הקודם")}
   FilledTonalButton(onClick=onPosition,enabled=!busy,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=10.dp,vertical=8.dp)){
    if(busy){CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))}
    Text(threadNavigationLabel(page,pages),modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    Spacer(Modifier.width(5.dp));Icon(AppIcons.ExpandMore,null,Modifier.size(18.dp))
   }
   IconButton(onClick=onNext,enabled=!busy&&page<pages){Icon(AppIcons.KeyboardArrowRight,"לעמוד הבא")}
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ThreadNavigationSheet(vm:AppModel,page:Int,pages:Int,total:Int,currentIndex:Int,resumeIndex:Int,resumeOffset:Int,onDismiss:()->Unit){
 val focus=LocalFocusManager.current
 var byPost by rememberSaveable{mutableStateOf(false)}
 var number by rememberSaveable{mutableStateOf(page.toString())}
 val limit=if(byPost)total else pages
 val valid=number.toIntOrNull()?.takeIf{it in 1..limit}
 fun move(action:()->Unit){focus.clearFocus();onDismiss();action()}
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("ניווט בדיון",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   Text("פוסט $currentIndex מתוך $total · ${threadNavigationLabel(page,pages)}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
    OutlinedButton(enabled=page>1,onClick={move{vm.navigateThread(page=page-1)}},modifier=Modifier.weight(1f)){Icon(AppIcons.PreviousPage,null,Modifier.size(16.dp));Text(" הקודם")}
    OutlinedButton(enabled=page<pages,onClick={move{vm.navigateThread(page=page+1)}},modifier=Modifier.weight(1f)){Text("הבא ");Icon(AppIcons.KeyboardArrowRight,null,Modifier.size(16.dp))}
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
    FilledTonalButton(onClick={move{vm.navigateThread(index=1,rememberReturn=true)}},modifier=Modifier.weight(1f),contentPadding=PaddingValues(8.dp)){
     Icon(AppIcons.FirstPage,null,Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text("תחילת הדיון")
    }
    FilledTonalButton(onClick={move{vm.navigateThread(page=pages,end=true,rememberReturn=true)}},modifier=Modifier.weight(1f),contentPadding=PaddingValues(8.dp)){
     Icon(AppIcons.LastPage,null,Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text("תגובה אחרונה")
    }
   }
   if(resumeIndex>0)OutlinedButton(onClick={move{vm.navigateThread(index=resumeIndex,offset=resumeOffset,rememberReturn=true)}},modifier=Modifier.fillMaxWidth()){Text("מקום הקריאה האחרון · פוסט $resumeIndex")}
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    FilterChip(selected=!byPost,onClick={byPost=false;number=page.toString()},label={Text("עמוד")})
    FilterChip(selected=byPost,onClick={byPost=true;number=currentIndex.toString()},label={Text("פוסט")})
   }
   OutlinedTextField(value=number,onValueChange={number=it.filter{c->c in '0'..'9'}.take(7)},singleLine=true,
    label={Text(if(byPost)"מספר פוסט"else "מספר עמוד")},supportingText={Text("בין 1 ל־$limit")},
    textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Ltr),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
   Button(enabled=valid!=null,onClick={valid?.let{target->move{if(byPost)vm.navigateThread(index=target,rememberReturn=true)else vm.navigateThread(page=target)}}},modifier=Modifier.fillMaxWidth()){Text("מעבר")}
   HorizontalDivider()
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("קריאה רציפה",fontWeight=FontWeight.SemiBold);Text("התגובות הבאות נטענות כשמתקרבים לסוף",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked=vm.continuousReading,onCheckedChange=vm::chooseContinuousReading)}
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ThreadExplorerSheet(vm:AppModel,replyTo:Post?,onDismiss:()->Unit,onPost:(Post)->Unit){
 var query by rememberSaveable(vm.route.tid){mutableStateOf(vm.threadScan.query)}
 val scan=vm.threadScan
 LaunchedEffect(replyTo?.id){replyTo?.let{vm.startThreadScan("",it.id)}}
 ModalBottomSheet(onDismissRequest={vm.stopThreadScan();onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
  Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text(if(replyTo!=null)"תגובות לפוסט #${replyTo.index}"else "חיפוש בתוך הדיון",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   if(replyTo!=null)Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(12.dp)){Text(replyTo.author,fontWeight=FontWeight.SemiBold);Text(threadSearchText(replyTo).take(160),maxLines=3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}}
   else {
    OutlinedTextField(value=query,onValueChange={query=it.take(120)},singleLine=true,label={Text("מילים לחיפוש")},modifier=Modifier.fillMaxWidth())
    Button(enabled=query.trim().isNotEmpty(),onClick={vm.startThreadScan(query)},modifier=Modifier.fillMaxWidth()){Icon(AppIcons.Search,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("חיפוש")}
   }
   if(scan.query.isNotBlank()||scan.replyTo>0){
    Text(if(scan.complete)"${scan.posts.size} תוצאות · החיפוש הושלם"else "${scan.posts.size} תוצאות · נבדקו ${scan.checkedPages.size} מתוך ${scan.totalPages} עמודים",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(scan.running){LinearProgressIndicator(Modifier.fillMaxWidth());OutlinedButton(onClick=vm::stopThreadScan){Text("עצירת החיפוש")}}
    else if(!scan.complete)FilledTonalButton(onClick={vm.startThreadScan(scan.query,scan.replyTo,continueScan=true)},modifier=Modifier.fillMaxWidth()){Text("המשך חיפוש")}
    if(scan.error.isNotBlank())Text(scan.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
   }
   LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=24.dp)){
    if(scan.posts.isEmpty()&&!scan.running)item{Text(if(scan.complete)"לא נמצאו תוצאות בדיון."else "חפשו כדי למצוא תגובות בדיון. התוצאות יופיעו כאן במהלך החיפוש.",style=MaterialTheme.typography.bodyMedium)}
    items(scan.posts,key={it.id}){post->OutlinedCard(onClick={vm.stopThreadScan();onDismiss();onPost(post)},modifier=Modifier.fillMaxWidth()){
     Column(Modifier.padding(14.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){UserAvatar(post.avatar,post.author,30.dp,online=post.online,uid=post.uid);Spacer(Modifier.width(8.dp));Text(post.author,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Text("#${post.index}",style=MaterialTheme.typography.labelMedium)}
      Text(threadSearchSnippet(post,scan.query),Modifier.padding(top=8.dp),maxLines=4,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
     }
    }}
   }
  }
 }
}
