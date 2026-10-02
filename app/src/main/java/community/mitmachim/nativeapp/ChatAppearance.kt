package community.mitmachim.nativeapp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import org.jsoup.Jsoup
import kotlinx.coroutines.launch

/** Positions are intentional, independent of text language: own left, everyone else right. */
fun chatBubbleOnRight(mine:Boolean)=!mine
class ChatBubbleShape(private val mine:Boolean):Shape {
 override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
  val right=chatBubbleOnRight(mine)
  val tail=with(density){6.dp.toPx()};val radius=with(density){8.dp.toPx()}
  val tipRadius=with(density){2.dp.toPx()};val overlap=with(density){2.dp.toPx()}
  val left=if(right)0f else tail;val end=if(right)size.width-tail else size.width
  return Outline.Generic(Path().apply{
   addRoundRect(RoundRect(left,0f,end,size.height,
    topLeftCornerRadius=CornerRadius(if(right)radius else tipRadius),
    topRightCornerRadius=CornerRadius(if(right)tipRadius else radius),
    bottomLeftCornerRadius=CornerRadius(radius),bottomRightCornerRadius=CornerRadius(radius)))
   val edge=if(right)end else left
   moveTo(if(right)edge-overlap else edge+overlap,0f)
   quadraticTo(if(right)size.width else 0f,0f,if(right)size.width else 0f,tipRadius)
   lineTo(edge,tail*1.35f);close()
  })
 }
}

@Composable fun ChatWallpaper(modifier:Modifier=Modifier){
 ChatDoodleWallpaper(modifier)
}

/** Plain short messages can shrink to their text; rich messages keep the existing safe renderer. */
fun chatPlainText(html:String):String? {
 val doc=Jsoup.parseBodyFragment(html)
 doc.select("script,style").remove()
 if(doc.select("a,img,pre,code,table,details,blockquote,video,audio,ul,ol,strong,b,em,i,h1,h2,h3,del,s").isNotEmpty())return null
 val result=StringBuilder()
 fun blockBreak(){if(result.isNotEmpty()&&result.last()!='\n')result.append('\n')}
 fun visit(node:org.jsoup.nodes.Node){
  when(node){
   is org.jsoup.nodes.TextNode->{val value=node.wholeText.replace(Regex("[\\t\\r\\n ]+")," ");if(value.isNotBlank()||result.isNotEmpty()&&result.last()!='\n')result.append(value)}
   is org.jsoup.nodes.Element->{
    if(node.normalName()=="br")result.append('\n')else{
     if(node.isBlock)blockBreak()
     node.childNodes().forEach(::visit)
     if(node.isBlock)blockBreak()
    }
   }
  }
 }
 doc.body().childNodes().forEach(::visit)
 return result.toString().replace('\u00a0',' ').replace(Regex(" +")," ").replace(Regex(" *\\n *"),"\n").replace(Regex("\\n{3,}"),"\n\n").trim().takeIf{it.isNotBlank()}
}

@Composable fun ChatInvitation(vm:AppModel){
 Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.065f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary.copy(alpha=.16f)),modifier=Modifier.fillMaxWidth().testTag("chat-invitation")){
  Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
     Icon(AppIcons.ChatBubbleOutline,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
     Text("מתמחים+, גם בתצוגת צ׳אט",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
    }
    Text("מראה שיחתי ונוח. אפשר לחזור בהגדרות.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
   FilledTonalButton(onClick={vm.chooseThreadStyle("chat")},contentPadding=PaddingValues(horizontal=12.dp,vertical=6.dp),modifier=Modifier.testTag("try-chat")){Text("לנסות")}
  }
 }
}

@Composable fun ChatAppHeader(vm:AppModel,title:String,inset:Boolean=true){
 var menu by remember{mutableStateOf(false)}
 Surface(color=MaterialTheme.colorScheme.surface){
  Row(Modifier.fillMaxWidth().then(if(inset)Modifier.statusBarsPadding()else Modifier).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically){
   if(vm.canBack)IconButton(enabled=!vm.sending,onClick={vm.back()}){Icon(AppIcons.ArrowBack,"חזרה")}
   Text(title,Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
   if(vm.route.kind !in listOf("editor","edit","login"))IconButton(onClick={vm.open(Route("search"))}){Icon(AppIcons.Search,"חיפוש")}
   Box{IconButton(onClick={menu=true}){Icon(AppIcons.More,"תפריט האפליקציה")}
    DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
     DropdownMenuItem(text={Text("התראות")},onClick={menu=false;vm.open(Route("notifications"))})
     DropdownMenuItem(text={Text("הודעות פרטיות")},onClick={menu=false;vm.open(Route("chats"))})
     DropdownMenuItem(text={Text("הגדרות")},onClick={menu=false;vm.open(Route("settings"))})
    }
   }
  }
 }
}

fun compactChatRows(widthDp:Int,heightDp:Int,fontScale:Float)=
 (widthDp<360||heightDp<700)&&fontScale<=1.2f

@Composable fun ChatTopicRow(topic:Topic,vm:AppModel){
 val position=vm.readIndex(topic.id)
 val continuing=position>0&&position<topic.posts
 val narrow=LocalConfiguration.current.screenWidthDp<340
 val configuration=LocalConfiguration.current
 val compact=compactChatRows(configuration.screenWidthDp,configuration.screenHeightDp,configuration.fontScale*vm.fontScale)
 Surface(onClick={vm.open(Route("topic",tid=topic.id))},color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().testTag("chat-topic-${topic.id}")){
  Row(Modifier.fillMaxWidth().heightIn(min=if(compact)72.dp else 80.dp).padding(horizontal=if(compact)12.dp else 14.dp,vertical=if(compact)8.dp else 11.dp),verticalAlignment=Alignment.CenterVertically){
   UserAvatar(topic.avatar,topic.author,if(narrow||compact)42.dp else 48.dp,online=topic.online,uid=topic.authorUid)
   Spacer(Modifier.width(if(narrow)9.dp else 12.dp))
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
    Text(topic.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Start)
    Text(topic.excerpt.ifBlank{"${topic.author} · ${topic.category}"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
   }
   Spacer(Modifier.width(8.dp))
   Column(Modifier.widthIn(max=if(narrow)56.dp else 72.dp),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text(when{topic.time<=0->"";chatDay(topic.time)==chatDay(System.currentTimeMillis())->chatTime(topic.time);else->java.text.SimpleDateFormat("dd.MM",java.util.Locale.US).format(java.util.Date(topic.time))},style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(continuing)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
     if(topic.pinned)Icon(AppIcons.PushPin,"נעוץ",Modifier.size(13.dp))
     if(topic.locked)Icon(AppIcons.Shield,"דיון נעול",Modifier.size(13.dp))
     if(vm.saved.any{it.id==topic.id})Icon(AppIcons.Bookmark,"שמור",Modifier.size(13.dp))
     if(continuing)Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary,CircleShape).semantics{contentDescription="יש המשך לקריאה"})
     Text(number((topic.posts-1).coerceAtLeast(0)),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }
  }
 }
 HorizontalDivider(Modifier.padding(start=74.dp,end=14.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
}

@Composable fun ChatCommunityRow(category:Category,vm:AppModel){
 val configuration=LocalConfiguration.current
 val compact=compactChatRows(configuration.screenWidthDp,configuration.screenHeightDp,configuration.fontScale*vm.fontScale)
 Surface(onClick={vm.openCategory(category)},color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()){
  Row(Modifier.heightIn(min=if(compact)72.dp else 80.dp).padding(horizontal=16.dp,vertical=if(compact)8.dp else 11.dp),verticalAlignment=Alignment.CenterVertically){
   Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primary.copy(alpha=.09f)){Box(Modifier.size(if(compact)42.dp else 48.dp),contentAlignment=Alignment.Center){Icon(categoryIcon(category.name),null,Modifier.size(25.dp),tint=MaterialTheme.colorScheme.primary)}}
   Spacer(Modifier.width(12.dp))
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(category.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text("${number(category.count)} נושאים",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   Icon(AppIcons.KeyboardArrowRight,"פתיחת הקטגוריה",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
 HorizontalDivider(Modifier.padding(start=74.dp,end=14.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ChatForumFeed(vm:AppModel,onLink:(String)->Unit){
 val route=vm.route;val home=route.kind=="home";val state=rememberLazyListState()
 var search by rememberSaveable{mutableStateOf("")}
 var options by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)){
  if(home)ChatAppHeader(vm,"מתמחים+")
  PullToRefreshBox(isRefreshing=vm.refreshing,onRefresh=vm::retry){
   LazyColumn(state=state,modifier=Modifier.fillMaxSize().testTag("home-feed"),contentPadding=PaddingValues(bottom=76.dp)){
    if(home)item{
     OutlinedTextField(value=search,onValueChange={search=it},placeholder={Text("סינון נושאים בעמוד")},leadingIcon={Icon(AppIcons.Search,null)},trailingIcon={if(search.isNotBlank())IconButton(onClick={vm.open(Route("search",query=search))}){Icon(AppIcons.Search,"חיפוש בכל הפורום")}},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={if(search.isNotBlank())vm.open(Route("search",query=search))}),singleLine=true,shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp))
     Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
      FilterChip(selected=true,onClick={},label={Text("הכול")})
      if(vm.account.uid>0)FilterChip(selected=false,onClick={vm.open(Route("unread"))},label={Text("לא נקראו")})
      FilterChip(selected=false,onClick={vm.open(Route("popular"))},label={Text("פופולריים")})
      FilterChip(selected=false,onClick={vm.root("saved")},label={Text("שמורים")})
      FilterChip(selected=false,onClick={vm.open(Route("unsolved"))},label={Text("לא נפתר")})
      FilterChip(selected=false,onClick={vm.open(Route("tags"))},label={Text("תגיות")})
     }
    }
    if(route.kind=="search")item{Box(Modifier.padding(14.dp)){SearchPanel(vm)}}
    if(route.kind=="category")item{
     val initial=(vm.page as? Load.Ready)?.value?.watched?:false
     var watched by remember(route.cid,initial){mutableStateOf(initial)}
     var busy by remember(route.cid){mutableStateOf(false)}
     Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){
      Text((vm.page as? Load.Ready)?.value?.name?:"נושאים",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
      Box{IconButton(onClick={options=true}){Icon(AppIcons.More,"פעולות הקטגוריה")};DropdownMenu(expanded=options,onDismissRequest={options=false}){
       DropdownMenuItem(text={Text(if(route.cid in vm.favoriteCategories)"הסרה ממועדפים"else "הוספה למועדפים")},onClick={options=false;vm.toggleFavorite(route.cid)})
       if(vm.account.uid>0)DropdownMenuItem(text={Text(if(watched)"הפסקת מעקב"else "מעקב אחר הקטגוריה")},enabled=!busy,onClick={options=false;if(!vm.demo){busy=true;scope.launch{try{vm.api.watchCategory(route.cid,watched);watched=!watched;vm.showMessage(if(watched)"המעקב הופעל"else "המעקב הופסק")}catch(e:Exception){vm.showMessage(friendlyError(e))}finally{busy=false}}}})
      }}
     }
    }
    when(val load=vm.page){
     Load.Waiting->item{Box(Modifier.padding(14.dp)){Skeletons()}}
     is Load.Failed->item{Box(Modifier.padding(14.dp)){ErrorCard(load.message){vm.retry()}}}
     is Load.Ready->{
      val page=load.value
      items(page.children,key={"community-${it.id}"}){ChatCommunityRow(it,vm)}
      if(page.link.isNotBlank())item{OutlinedButton(onClick={onLink(page.link)},modifier=Modifier.padding(14.dp)){Text("פתיחת קישור הקטגוריה")}}
      val shown=page.topics.filter{!home||search.isBlank()||it.title.contains(search,true)||it.category.contains(search,true)||it.excerpt.contains(search,true)}
      if(shown.isEmpty()&&page.children.isEmpty()&&page.link.isBlank())item{Box(Modifier.padding(14.dp)){EmptyCard("אין נושאים להצגה",if(search.isBlank())"נסו קטגוריה או עמוד אחר."else "אפשר לחפש גם בכל הפורום.")}}
      items(shown,key={it.id}){ChatTopicRow(it,vm)}
      if(home&&search.isNotBlank())item{FilledTonalButton(onClick={vm.open(Route("search",query=search))},modifier=Modifier.fillMaxWidth().padding(14.dp)){Text("חיפוש בכל הפורום")}}
      if(page.pages>1)item{Box(Modifier.padding(horizontal=14.dp,vertical=8.dp)){Pager(page.page,page.pages){vm.open(route.copy(page=it))}}}
     }
    }
   }
  }
 }
}

@Composable fun ChatCategories(vm:AppModel){
 var query by rememberSaveable{mutableStateOf("")}
 LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)){
  item{OutlinedTextField(value=query,onValueChange={query=it},placeholder={Text("חיפוש קהילה או תחום")},leadingIcon={Icon(AppIcons.Search,null)},singleLine=true,shape=RoundedCornerShape(26.dp),modifier=Modifier.fillMaxWidth().padding(14.dp))}
  when(val load=vm.directory){
   Load.Waiting->item{Box(Modifier.padding(14.dp)){CategorySkeletons()}}
   is Load.Failed->item{Box(Modifier.padding(14.dp)){ErrorCard(load.message){vm.reloadDirectory()}}}
   is Load.Ready->{val shown=if(query.isBlank())load.value else flatten(load.value).filter{it.name.contains(query,true)}
    if(shown.isEmpty())item{Box(Modifier.padding(14.dp)){EmptyCard("לא נמצאה קטגוריה","נסו מילה אחרת.")}}
    items(shown,key={it.id}){ChatCommunityRow(it,vm)}
   }
  }
 }
}
