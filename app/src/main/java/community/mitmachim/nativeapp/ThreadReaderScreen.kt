package community.mitmachim.nativeapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable fun ThreadScreen(vm:AppModel,onLink:(String)->Unit,onSource:(String)->Unit){
 val chat=vm.threadStyle=="chat"
 MaterialTheme(colorScheme=discussionColorScheme(MaterialTheme.colorScheme,chat),typography=if(chat)MaterialTheme.typography else DiscussionTypography){
  CompositionLocalProvider(LocalDiscussionSans provides !chat){ThreadReader(vm,onLink,onSource)}
 }
}

@OptIn(ExperimentalMaterial3Api::class,FlowPreview::class)
@Composable private fun ThreadReader(vm:AppModel,onLink:(String)->Unit,onSource:(String)->Unit){
 val route=vm.route
 val state=rememberLazyListState()
 val data=(vm.thread as? Load.Ready)?.value?.takeIf{it.topic.id==route.tid}
 val window=vm.threadWindow
 val posts=data?.posts.orEmpty()
 val postPositions=remember(posts){posts.mapIndexed{index,post->"post-${post.id}" to index}.toMap()}
 val readAtOpen=remember(route.tid){vm.readIndex(route.tid)}
 val resumeIndex=remember(route.tid){vm.lastPosition(route.tid)}
 val resumeOffset=remember(route.tid){vm.lastPositionOffset(route.tid)}
 var navigation by rememberSaveable(route.tid){mutableStateOf(false)}
 var explorer by remember(route.tid){mutableStateOf(false)}
 var replyExplorer by remember(route.tid){mutableStateOf<Post?>(null)}
 var replyRequest by remember(route.tid){mutableStateOf<Post?>(null)}
 var replyRequestId by remember(route.tid){mutableIntStateOf(0)}
 var topicInfo by remember(route.tid){mutableStateOf(false)}
 var railPreview by remember(route.tid){mutableStateOf<Int?>(null)}
 var railVisible by remember(route.tid){mutableStateOf(false)}
 val chat=vm.threadStyle=="chat"
 LaunchedEffect(state.isScrollInProgress,railPreview,chat,data!=null){
  if(chat||data==null)railVisible=false
  else if(threadRailActive(state.isScrollInProgress,railPreview!=null))railVisible=true
  else {delay(1400);railVisible=false}
 }
 val dark=MaterialTheme.colorScheme.background.luminance()<.3f
 val compactTitle by remember(state){derivedStateOf{state.firstVisibleItemIndex>0}}
 val currentVisible by remember(state){derivedStateOf{state.layoutInfo.visibleItemsInfo.firstOrNull{it.key.toString().startsWith("post-")}}}
 val currentPost=posts.firstOrNull{"post-${it.id}"==currentVisible?.key}
 val currentIndex=currentPost?.index?:posts.firstOrNull()?.index?:1
 val currentPage=currentPost?.let{window?.pageOf(it.id)}?:data?.page?:route.page
 fun rememberViewport(){currentPost?.let{vm.rememberThreadViewport(it,(-(currentVisible?.offset?:0)).coerceAtLeast(0))}}
 fun goPost(post:Post){rememberViewport();vm.navigateThread(index=post.index,pid=post.id,rememberReturn=true)}
 fun openReplies(post:Post){rememberViewport();replyExplorer=post;explorer=true}
 fun reply(post:Post){replyRequest=post;replyRequestId++;vm.quickReplyOpen=true}
 fun keepReadingAnchor(){if(!state.isScrollInProgress)state.requestScrollToItem(state.firstVisibleItemIndex,state.firstVisibleItemScrollOffset)}
 fun loadMore(before:Boolean=false){
  rememberViewport()
  vm.loadAdjacentThreadPage(before){merged->
   val visible=state.layoutInfo.visibleItemsInfo.firstOrNull{it.key.toString().startsWith("post-")}
   val pid=visible?.key?.toString()?.removePrefix("post-")?.toIntOrNull()
   if(visible!=null&&pid!=null)threadAnchorAfterMerge(posts.map{it.id},merged.map{it.id},pid,-visible.offset)?.let{anchor->
    state.requestScrollToItem(anchor.postIndex+3,anchor.scrollOffset)
   }
  }
 }
 val wrappedLink:(String)->Unit={value->rememberViewport();onLink(value)}

 LaunchedEffect(route.tid,vm.threadScrollRequest){
  if(data==null||vm.threadNavigating)return@LaunchedEffect
  val target=when{route.pid>0->posts.indexOfFirst{it.id==route.pid};route.index>0->posts.indexOfFirst{it.index==route.index};else->-1}
  when{
   route.end->state.scrollToItem(posts.size+3)
   route.index==1&&route.pid==0->state.scrollToItem(0)
   target>=0->state.scrollToItem(target+3,route.scrollOffset)
   route.page==1->state.scrollToItem(0)
   posts.isNotEmpty()->state.scrollToItem(3)
  }
 }
 val latestPosts by rememberUpdatedState(posts)
 LaunchedEffect(route.tid,state){
  snapshotFlow{state.layoutInfo.visibleItemsInfo.firstOrNull{it.key.toString().startsWith("post-")}?.let{it.key.toString() to (-it.offset).coerceAtLeast(0)}}
   .distinctUntilChanged().debounce(220).collect{visible->
    visible?.let{(key,offset)->latestPosts.firstOrNull{"post-${it.id}"==key}?.let{vm.rememberThreadViewport(it,offset)}}
   }
 }
 val uid=vm.account.uid
 DisposableEffect(route.tid,uid){onDispose{
  if(uid==vm.account.uid){val visible=state.layoutInfo.visibleItemsInfo.firstOrNull{it.key.toString().startsWith("post-")};latestPosts.firstOrNull{"post-${it.id}"==visible?.key}?.let{vm.rememberPosition(route.tid,it.index,(-(visible?.offset?:0)).coerceAtLeast(0))}}
 }}
 LaunchedEffect(route.tid,window?.firstPage,window?.lastPage,vm.continuousReading,vm.threadAdjacentError,vm.threadNavigating){
  if(!vm.continuousReading||data==null||vm.threadNavigating||vm.threadAdjacentError.isNotBlank()||window==null)return@LaunchedEffect
  var previousItem=state.firstVisibleItemIndex;var previousOffset=state.firstVisibleItemScrollOffset
  snapshotFlow{
   val visible=state.layoutInfo.visibleItemsInfo.mapNotNull{item->postPositions[item.key]}
   ThreadPrefetchViewport(state.firstVisibleItemIndex,state.firstVisibleItemScrollOffset,visible.firstOrNull()?:-1,visible.lastOrNull()?:-1,state.isScrollInProgress,vm.threadPageLoading!=0)
  }.distinctUntilChanged().collect{view->
   val backwards=view.scrolling&&(view.item<previousItem||view.item==previousItem&&view.offset<previousOffset)
   previousItem=view.item;previousOffset=view.offset
   if(!view.loading)threadPrefetchDirection(view.first,view.last,posts.size,window.firstPage>1,window.lastPage<data.pages,backwards)?.let{loadMore(it)}
  }
 }
 Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding().testTag("thread-screen")){
  val toolbarColor=if(chat)MaterialTheme.colorScheme.surface else Color(0xFF007F89)
  val toolbarText=if(chat)MaterialTheme.colorScheme.onSurface else Color.White
  val narrowToolbar=LocalConfiguration.current.screenWidthDp<360||LocalDensity.current.fontScale>1.2f
  Row(Modifier.fillMaxWidth().then(if(chat)Modifier.background(toolbarColor)else Modifier.clip(RoundedCornerShape(bottomStart=24.dp,bottomEnd=24.dp)).background(Brush.verticalGradient(listOf(Color(0xFF007F89),Color(0xFF235DA4))))).statusBarsPadding().then(if(chat)Modifier else Modifier.heightIn(min=76.dp)).padding(horizontal=4.dp).testTag("thread-toolbar"),verticalAlignment=Alignment.CenterVertically){
   IconButton(modifier=Modifier.size(36.dp),enabled=!vm.sending,onClick={rememberViewport();vm.back()}){Icon(AppIcons.ArrowBack,"חזרה",tint=toolbarText)}
   Column(Modifier.weight(1f).clickable(enabled=data!=null){topicInfo=true}.padding(vertical=if(chat)6.dp else 14.dp).testTag("thread-title")){
    Text(if(!chat&&!compactTitle)"דיון"else data?.topic?.title?:"דיון",style=MaterialTheme.typography.titleMedium.copy(fontSize=if(chat){if(narrowToolbar)18.sp else 20.sp}else if(narrowToolbar)20.sp else 22.sp,lineHeight=if(chat){if(narrowToolbar)22.sp else 25.sp}else if(narrowToolbar)26.sp else 29.sp),fontWeight=FontWeight.SemiBold,color=toolbarText,maxLines=2,overflow=TextOverflow.Ellipsis)
    if(chat)data?.let{Text(it.topic.category,style=MaterialTheme.typography.labelSmall,color=toolbarText.copy(alpha=.7f),maxLines=1,overflow=TextOverflow.Ellipsis)}
   }
   if(data!=null){
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp){
     FilledTonalButton(enabled=!vm.threadNavigating&&!vm.sending,onClick={rememberViewport();navigation=true},modifier=Modifier.padding(horizontal=4.dp).testTag("thread-position"),contentPadding=PaddingValues(horizontal=9.dp,vertical=5.dp)){
      Text(if(narrowToolbar)"${railPreview?:currentIndex}"else "${railPreview?:currentIndex} / ${data.topic.posts}",style=MaterialTheme.typography.labelSmall.copy(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr),maxLines=1)
     }
    }
    if(vm.threadReturn!=null)IconButton(modifier=Modifier.size(32.dp),enabled=!vm.sending,onClick={rememberViewport();vm.returnToThreadPosition()}){Icon(AppIcons.Reply,"חזרה למקום הקודם · פוסט ${vm.threadReturn?.index}",tint=toolbarText)}
    else if(!chat&&!data.locked&&data.canReply)IconButton(modifier=Modifier.size(32.dp),enabled=!vm.sending,onClick={vm.quickReplyOpen=true}){Icon(AppIcons.Reply,"כתיבת תגובה",tint=toolbarText)}
    ThreadTopicMenu(vm,data,toolbarText,{rememberViewport();replyExplorer=null;explorer=true},{rememberViewport();navigation=true},onSource)
   }
  }
  if(vm.threadNavigating&&data!=null)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
  if(vm.threadNavigationError.isNotBlank())Surface(color=MaterialTheme.colorScheme.errorContainer){
   Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){
    Text(vm.threadNavigationError,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
    IconButton(onClick=vm::retryThreadNavigation){Icon(AppIcons.Reply,"ניסיון נוסף")}
    IconButton(onClick={currentPost?.let{vm.navigateThread(index=it.index,pid=it.id)}}){Icon(AppIcons.Close,"הישארו כאן")}
   }
  }
  Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background)){
   if(chat)ChatWallpaper(Modifier.matchParentSize().testTag("chat-discussion-wallpaper"))
   PullToRefreshBox(isRefreshing=vm.refreshing,onRefresh={rememberViewport();vm.retry()},modifier=Modifier.fillMaxSize()){
    LazyColumn(state=state,modifier=Modifier.fillMaxSize().testTag("thread-posts"),contentPadding=PaddingValues(top=6.dp,bottom=12.dp)){
     if(data==null){when(val load=vm.thread){is Load.Failed->item{Box(Modifier.padding(18.dp)){ErrorCard(load.message){vm.retry()}}};else->item{ThreadLoading()}}}
     else{
      // Keep structural slots stable so navigation does not depend on presentation mode.
      item(key="heading"){if(!chat)DiscussionHeading(data,narrowToolbar){topicInfo=true}}
      item(key="topic-tools"){if(!chat)DiscussionTools(vm,data,readAtOpen,{rememberViewport()},onSource)}
      item(key="previous-page"){
       if((window?.firstPage?:1)>1)ThreadPageLoader("טעינת תגובות קודמות",vm.threadPageLoading==window!!.firstPage-1,vm.threadPageLoading!=0||vm.threadNavigating,if(vm.threadPageLoading==0)vm.threadAdjacentError else ""){loadMore(before=true)}
      }
      itemsIndexed(posts,key={_,post->"post-${post.id}"}){index,post->
       val previous=posts.getOrNull(index-1)
       Column(Modifier.fillMaxWidth()){
        if(chat&&chatDay(post.time).isNotBlank()&&chatDay(previous?.time?:0)!=chatDay(post.time))Box(Modifier.fillMaxWidth().padding(vertical=8.dp),contentAlignment=Alignment.Center){
         Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.9f)){Text(chatDay(post.time),Modifier.padding(horizontal=10.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall)}
        }
        if(readAtOpen>0&&post.index>readAtOpen&&(previous==null||previous.index<=readAtOpen))Box(Modifier.fillMaxWidth().padding(vertical=7.dp),contentAlignment=Alignment.Center){Badge("מכאן לא נקרא")}
        ThreadPost(vm,post,data.topic,posts,chat,sameChatGroup(previous,post),!data.locked&&data.canReply,wrappedLink,::reply,::goPost,::openReplies,::keepReadingAnchor)
       }
      }
      item(key="next-page"){
       if((window?.lastPage?:data.page)<data.pages)ThreadPageLoader("טעינת התגובות הבאות",vm.threadPageLoading==(window?.lastPage?:data.page)+1,vm.threadNavigating||vm.threadPageLoading!=0,vm.threadAdjacentError){loadMore()}
       else Text("הגעתם לסוף הדיון",Modifier.fillMaxWidth().padding(12.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      }
     }
    }
   }
   androidx.compose.animation.AnimatedVisibility(visible=!chat&&data!=null&&railVisible,enter=fadeIn(),exit=fadeOut(),modifier=Modifier.align(AbsoluteAlignment.CenterLeft)){
    BoxWithConstraints(Modifier.width(18.dp).fillMaxHeight().testTag("thread-side-navigation")){
    val railHeight=minOf(maxHeight,180.dp)
    val enabled=!vm.threadNavigating&&!vm.sending
    fun railJump(index:Int){rememberViewport();vm.navigateThread(index=index,rememberReturn=true)}
    Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){
     CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp){
      if(data!=null&&railHeight>=24.dp)ThreadProgressRail(threadProgress(currentIndex,data.topic.posts),data.topic.posts,{railPreview=it},::railJump,enabled,railHeight,width=18.dp)
     }
    }
    }
   }
  }
  if(data!=null){
   if(threadReplyAvailability(data.locked,data.canReply,vm.account.uid)==ThreadReplyAvailability.Compose)
    QuickReplyComposer(vm,data.topic,replyRequest,replyRequestId,chat=chat,onRequestConsumed={replyRequest=null})
   else ThreadReplyNotice(vm,threadReplyAvailability(data.locked,data.canReply,vm.account.uid))
  }
 }
 if(navigation&&data!=null)ThreadNavigationSheet(vm,currentPage,data.pages,data.topic.posts,currentIndex,resumeIndex,resumeOffset){navigation=false}
 if(explorer&&data!=null)ThreadExplorerSheet(vm,replyExplorer,{explorer=false},::goPost)
 if(topicInfo&&data!=null)ThreadInfoDialog(data){topicInfo=false}
}

@Composable private fun ThreadInfoDialog(data:ThreadPage,onDismiss:()->Unit){
 AlertDialog(onDismissRequest=onDismiss,title={Text(data.topic.title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text(data.topic.category);Text("${number(data.topic.posts)} פוסטים · ${number(data.topic.views)} צפיות")
  if(data.locked)Text("הדיון נעול");if(data.topic.solved)Text("הדיון מסומן כנפתר")
 }},confirmButton={FilledTonalButton(onClick=onDismiss){Text("סגירה")}})
}

@Composable private fun ThreadReplyNotice(vm:AppModel,availability:ThreadReplyAvailability){
 Surface(color=MaterialTheme.colorScheme.surface){
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp).testTag("thread-reply-notice"),verticalAlignment=Alignment.CenterVertically){
   Text(when(availability){
    ThreadReplyAvailability.Locked->"הדיון נעול — לא ניתן להוסיף תגובות."
    ThreadReplyAvailability.Login->"כדי לכתוב תגובה, יש להיכנס לחשבון."
    else->"הפורום אינו מאפשר לחשבון שלך להגיב בדיון הזה."
   },Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   if(availability==ThreadReplyAvailability.Login)FilledTonalButton(onClick={vm.open(Route("login"))},modifier=Modifier.padding(start=8.dp)){Text("כניסה")}
  }
 }
}

@Composable private fun ThreadTopicMenu(vm:AppModel,data:ThreadPage,tint:Color,onSearch:()->Unit,onNavigation:()->Unit,onSource:(String)->Unit){
 var menu by remember{mutableStateOf(false)}
 var info by remember{mutableStateOf(false)}
 var following by remember(data.topic.id,data.following){mutableStateOf(data.following)}
 var busy by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 Box{
  IconButton(modifier=Modifier.size(32.dp),enabled=!vm.sending,onClick={menu=true}){Icon(AppIcons.More,"פעולות הדיון",tint=tint)}
  DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
   DropdownMenuItem(text={Text("פרטי הדיון")},onClick={menu=false;info=true})
   DropdownMenuItem(text={Text("חיפוש בתוך הדיון")},leadingIcon={Icon(AppIcons.Search,null)},onClick={menu=false;onSearch()})
   DropdownMenuItem(text={Text("ניווט בדיון")},onClick={menu=false;onNavigation()})
   if(vm.threadReturn!=null)DropdownMenuItem(text={Text("חזרה למקום הקודם")},onClick={menu=false;vm.returnToThreadPosition()})
   if(!data.locked&&data.canReply)DropdownMenuItem(text={Text("כתיבת תגובה")},onClick={menu=false;vm.quickReplyOpen=true})
   DropdownMenuItem(text={Text(if(vm.saved.any{it.id==data.topic.id})"הסרה מהשמורים"else "שמירת הדיון")},leadingIcon={Icon(AppIcons.BookmarkBorder,null)},onClick={menu=false;vm.toggleSaved(data.topic)})
   if(vm.threadStyle=="chat")DropdownMenuItem(text={Text(if(following)"הפסקת מעקב"else "מעקב אחר הדיון")},enabled=!busy,onClick={
    menu=false
    if(vm.account.uid<=0)vm.open(Route("login"))else if(!vm.demo){busy=true;scope.launch{
     try{vm.api.follow(data.topic.id,following);following=!following;vm.showMessage(if(following)"המעקב הופעל"else "המעקב הופסק")}catch(e:Exception){vm.showMessage(friendlyError(e))}finally{busy=false}
    }}
   })
   val unread=vm.readIndex(data.topic.id)
   if(unread>0&&unread<data.topic.posts)DropdownMenuItem(text={Text("הראשון שלא נקרא")},onClick={menu=false;vm.navigateThread(index=unread+1,rememberReturn=true)})
   if(vm.account.uid>0)DropdownMenuItem(text={Text("סימון כנקרא")},onClick={menu=false;vm.markCurrentThreadRead()})
   DropdownMenuItem(text={Text("פתיחה באתר")},onClick={menu=false;onSource("$FORUM/topic/${data.topic.id}?page=${vm.threadVisiblePage}")})
  }
 }
 if(info)ThreadInfoDialog(data){info=false}
}

@Composable private fun ThreadPageLoader(label:String,loading:Boolean,disabled:Boolean,error:String,onLoad:()->Unit){
 Column(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=12.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally){
  if(error.isNotBlank())Text(error,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(bottom=4.dp))
  if(loading)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
  else FilledTonalButton(onClick=onLoad,enabled=!disabled,contentPadding=PaddingValues(horizontal=12.dp,vertical=6.dp)){Text(if(error.isNotBlank())"ניסיון נוסף"else label,style=MaterialTheme.typography.labelSmall)}
 }
}

@Composable private fun ThreadPost(vm:AppModel,post:Post,topic:Topic,posts:List<Post>,chat:Boolean,grouped:Boolean,canReply:Boolean,
 onLink:(String)->Unit,onReply:(Post)->Unit,onJump:(Post)->Unit,onReplies:(Post)->Unit,onContentChange:()->Unit){
 val context=LocalContext.current
 val mine=post.uid>0&&(post.uid==vm.account.uid||vm.demo&&post.uid==999)
 val dark=MaterialTheme.colorScheme.background.luminance()<.3f
 val original=posts.firstOrNull{it.id==post.toPid}
 val color=if(chat)chatMessageColor(mine,dark)else discussionPostColor(MaterialTheme.colorScheme,post.index)
 var swipeDistance by remember(post.id){mutableFloatStateOf(0f)}
 BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=if(chat&&grouped)1.dp else if(chat)3.dp else 6.dp)){
  val avatarSize=if(maxWidth<300.dp)28.dp else 32.dp
  val bubbleWidth=if(chat)minOf(maxWidth*.85f,maxWidth*.93f-avatarSize-6.dp)else maxWidth
  Column(Modifier.fillMaxWidth(),horizontalAlignment=if(chat&&chatBubbleOnRight(mine))AbsoluteAlignment.Right else if(chat)AbsoluteAlignment.Left else Alignment.Start){
   Box{
    if(kotlin.math.abs(swipeDistance)>8f)Icon(AppIcons.Reply,null,Modifier.align(if(swipeDistance>0)AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight).padding(horizontal=12.dp).size(20.dp),tint=MaterialTheme.colorScheme.primary)
    Row(verticalAlignment=if(chat)Alignment.Top else Alignment.Bottom,modifier=Modifier.then(if(chat)Modifier.testTag("chat-row-${post.id}")else Modifier).swipeToReply(canSwipeReply(canReply,post.deleted,vm.sending),{swipeDistance=it},{onReply(post)}).graphicsLayer{translationX=swipeDistance}){
     if(chat){UserAvatar(post.avatar,post.author,avatarSize,Modifier.testTag("chat-avatar-${post.id}").clickable(enabled=post.authorSlug.isNotBlank()){vm.open(Route("user",query=post.authorSlug))},online=post.online,uid=post.uid);Spacer(Modifier.width(6.dp))}
   Surface(color=color,shape=if(chat)ChatBubbleShape(mine)else RoundedCornerShape(26.dp),tonalElevation=if(chat)0.dp else 1.dp,
    border=if(chat)BorderStroke(if(mine)1.dp else .5.dp,if(mine)MaterialTheme.colorScheme.primary.copy(alpha=.6f)else MaterialTheme.colorScheme.outlineVariant.copy(alpha=.35f))else BorderStroke(1.dp,if(post.index==1)MaterialTheme.colorScheme.primary.copy(alpha=.25f)else MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f)),
    modifier=Modifier.widthIn(max=bubbleWidth).then(if(chat)Modifier.wrapContentWidth()else Modifier.fillMaxWidth()).testTag(if(chat)"chat-post-${post.id}"else "discussion-post-${post.id}")){
    Column(Modifier.padding(start=if(chat)11.dp else 18.dp,end=if(chat)11.dp else 18.dp,top=if(chat)6.dp else 18.dp,bottom=if(chat)2.dp else 15.dp)){
     if(!chat){DiscussionPostAuthor(vm,post);Spacer(Modifier.height(15.dp))}
     else if(!grouped&&!mine){
      Row(verticalAlignment=Alignment.CenterVertically){
       Surface(onClick={if(post.authorSlug.isNotBlank())vm.open(Route("user",query=post.authorSlug))},color=Color.Transparent,shape=RoundedCornerShape(6.dp)){
        Text(post.author,Modifier.padding(vertical=2.dp),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
       }
      };Spacer(Modifier.height(if(chat)2.dp else 6.dp))
     }
     if(post.toPid>0)Surface(onClick={if(original!=null)onJump(original)else vm.openPost(post.toPid)},color=MaterialTheme.colorScheme.onSurface.copy(alpha=.055f),shape=RoundedCornerShape(6.dp),modifier=Modifier.widthIn(max=bubbleWidth-22.dp).padding(bottom=5.dp)){
      Row(Modifier.padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
       Box(Modifier.width(3.dp).height(28.dp).background(MaterialTheme.colorScheme.primary));Spacer(Modifier.width(7.dp))
       Column{Text(original?.author?:"בתגובה לפוסט קודם",style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.SemiBold);Text(original?.let(::threadSearchText)?.take(90)?:"פתיחת ההודעה המקורית",style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)}
      }
     }
     if(post.deleted)Text("הפוסט הוסר בפורום",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     else ThreadMessageBody(post,vm,chat,onLink,onContentChange)
     if(chat)Row(Modifier.align(Alignment.End).padding(top=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
      Text(chatTime(post.time),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      if(!post.deleted)PostActions(vm,post,topic,canReply,{shareText(context,"${topic.title}\n$FORUM/post/${post.id}")},onReply={onReply(post)},onReplies={onReplies(post)},compact=true)
     }else if(!post.deleted){
      HorizontalDivider(Modifier.padding(top=15.dp,bottom=10.dp),color=MaterialTheme.colorScheme.outlineVariant)
      PostActions(vm,post,topic,canReply,{shareText(context,"${topic.title}\n$FORUM/post/${post.id}")},onReply={onReply(post)},onReplies={onReplies(post)})
     }
    }
   }
    }
   }
  }
 }
}

@Composable private fun ThreadMessageBody(post:Post,vm:AppModel,chat:Boolean,onLink:(String)->Unit,onContentChange:()->Unit){
 val text=remember(post.html){chatPlainText(post.html)}
 val preview=remember(post.html){threadSearchText(post)}
 val longRich=text==null&&(preview.length>650||preview.count{it=='\n'}>6)
 var expanded by rememberSaveable(post.id){mutableStateOf(false)}
 var overflows by remember(post.html){mutableStateOf(false)}
 if(chat&&text!=null){
  androidx.compose.foundation.text.selection.SelectionContainer{
   Text(text,style=MaterialTheme.typography.bodyLarge.copy(textDirection=androidx.compose.ui.text.style.TextDirection.Content),
    maxLines=if(expanded)Int.MAX_VALUE else 6,overflow=TextOverflow.Ellipsis,onTextLayout={if(!expanded)overflows=it.hasVisualOverflow})
  }
 }else if(chat&&longRich&&!expanded)Text(preview,style=MaterialTheme.typography.bodyLarge,maxLines=6,overflow=TextOverflow.Ellipsis)
 else RichContent(post.html,vm,onLink,compact=chat)
 if(chat&&(longRich||overflows||expanded)){
  Surface(onClick={onContentChange();expanded=!expanded},shape=RoundedCornerShape(6.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.10f),modifier=Modifier.padding(top=4.dp)){
   Row(Modifier.padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
    Text(if(expanded)"צמצום ההודעה"else "הצגת ההמשך",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
    Icon(if(expanded)AppIcons.ExpandLess else AppIcons.ExpandMore,null,Modifier.size(14.dp))
   }
  }
 }
}
