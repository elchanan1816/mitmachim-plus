package community.mitmachim.nativeapp

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity:ComponentActivity(){
 private var openNotices by mutableStateOf(false)
 private var openUpdates by mutableStateOf(false)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);RuntimeSafety.offlineDemo=(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0&&intent.getBooleanExtra("local_demo",false);openNotices=intent.getBooleanExtra("open_notifications",false);openUpdates=intent.getBooleanExtra("open_updates",false);enableEdgeToEdge();setContent{val vm:AppModel=viewModel();LaunchedEffect(openNotices,openUpdates){if(openUpdates){vm.open(Route("about"));openUpdates=false}else if(openNotices){vm.open(Route("notifications"));openNotices=false}};CommunityApp(vm)}}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);openNotices=intent.getBooleanExtra("open_notifications",false);openUpdates=intent.getBooleanExtra("open_updates",false)}
}
val Teal=Color(0xFF00838F);val accents=listOf(Teal,Color(0xFF087363),Color(0xFF1976B8),Color(0xFF8A3AB9),Color(0xFFC31960),Color(0xFFAC7400))
val Heebo=FontFamily(Font(R.font.heebo))
private val baseType=Typography()
val AppTypography=Typography(
 displayLarge=baseType.displayLarge.copy(fontFamily=Heebo),displayMedium=baseType.displayMedium.copy(fontFamily=Heebo),displaySmall=baseType.displaySmall.copy(fontFamily=Heebo),
 headlineLarge=baseType.headlineLarge.copy(fontFamily=Heebo),headlineMedium=baseType.headlineMedium.copy(fontFamily=Heebo),headlineSmall=baseType.headlineSmall.copy(fontFamily=Heebo),
 titleLarge=baseType.titleLarge.copy(fontFamily=Heebo),titleMedium=baseType.titleMedium.copy(fontFamily=Heebo),titleSmall=baseType.titleSmall.copy(fontFamily=Heebo),
 bodyLarge=baseType.bodyLarge.copy(fontFamily=Heebo,fontSize=17.sp,lineHeight=26.sp),bodyMedium=baseType.bodyMedium.copy(fontFamily=Heebo),bodySmall=baseType.bodySmall.copy(fontFamily=Heebo),
 labelLarge=baseType.labelLarge.copy(fontFamily=Heebo),labelMedium=baseType.labelMedium.copy(fontFamily=Heebo),labelSmall=baseType.labelSmall.copy(fontFamily=Heebo))
private fun chatText(style:TextStyle)=style.copy(fontFamily=FontFamily.SansSerif,letterSpacing=0.sp,
 platformStyle=PlatformTextStyle(includeFontPadding=false),lineHeightStyle=LineHeightStyle(LineHeightStyle.Alignment.Center,LineHeightStyle.Trim.Both))
val ChatTypography=Typography(
 displayLarge=chatText(baseType.displayLarge),displayMedium=chatText(baseType.displayMedium),displaySmall=chatText(baseType.displaySmall),
 headlineLarge=chatText(baseType.headlineLarge),headlineMedium=chatText(baseType.headlineMedium),headlineSmall=chatText(baseType.headlineSmall),
 titleLarge=chatText(baseType.titleLarge).copy(fontSize=24.sp,lineHeight=29.sp),titleMedium=chatText(baseType.titleMedium).copy(fontSize=18.sp,lineHeight=23.sp),titleSmall=chatText(baseType.titleSmall).copy(fontSize=16.sp,lineHeight=21.sp),
 bodyLarge=chatText(baseType.bodyLarge).copy(fontSize=16.sp,lineHeight=21.sp),bodyMedium=chatText(baseType.bodyMedium).copy(fontSize=15.sp,lineHeight=20.sp),bodySmall=chatText(baseType.bodySmall).copy(fontSize=14.sp,lineHeight=18.sp),
 labelLarge=chatText(baseType.labelLarge),labelMedium=chatText(baseType.labelMedium),labelSmall=chatText(baseType.labelSmall))
val round=RoundedCornerShape(26.dp)
fun number(n:Int)=NumberFormat.getIntegerInstance(Locale.US).format(n)
fun ago(time:Long):String {if(time<=0)return "";val m=((System.currentTimeMillis()-time)/60000).coerceAtLeast(0);return when{m<1->"עכשיו";m<60->"לפני $m דק׳";m<1440->"לפני ${m/60} שעות";m<10080->"לפני ${m/1440} ימים";else->java.text.SimpleDateFormat("dd.MM.yy",Locale.US).format(java.util.Date(time))}}
@Composable fun CommunityApp(vm:AppModel){
 val dark=when(vm.theme){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
 val scheme=if(dark)darkColorScheme(primary=Color(0xFF71D5DA),secondary=Color(0xFFA9C9FA),background=Color(0xFF101C26),surface=Color(0xFF192B36),surfaceContainer=Color(0xFF213642),onSurface=Color(0xFFE5F1F3),onSurfaceVariant=Color(0xFFB0C5CC),outlineVariant=Color(0xFF314A56))else lightColorScheme(primary=Teal,secondary=Color(0xFF2262A5),background=Color(0xFFF3F6F7),surface=Color.White,surfaceContainer=Color(0xFFEAF1F3),onSurface=Color(0xFF132D35),onSurfaceVariant=Color(0xFF536B73),outlineVariant=Color(0xFFDFE7E9))
 val appScheme=chatAppearanceScheme(scheme,vm.threadStyle=="chat",dark)
 val original=LocalDensity.current
 MaterialTheme(colorScheme=appScheme,typography=if(vm.threadStyle=="chat")ChatTypography else AppTypography){CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,LocalDensity provides Density(original.density,original.fontScale*vm.fontScale)){
  var link by remember {mutableStateOf<String?>(null)};val context=LocalContext.current
  val onLink:(String)->Unit={value->val target=routeFromLink(value);val pid=postIdFromLink(value);when{target!=null->vm.open(target);pid!=null->vm.openPost(pid);else->link=safeLink(value)?.toString()}}
  LaunchedEffect(vm.browserRequest){vm.browserRequest?.let{link=it;vm.consumeBrowserRequest()}}
  val stateHolder=rememberSaveableStateHolder()
  val snackbars=remember{SnackbarHostState()}
  LaunchedEffect(vm.banner){if(vm.banner.isNotEmpty()){snackbars.showSnackbar(vm.banner);vm.clearMessage()}}
  BackHandler(vm.canBack){if(vm.sending)vm.showMessage("השליחה בעיצומה. המתינו לסיומה.")else vm.back()}
  Scaffold(snackbarHost={SnackbarHost(snackbars)},containerColor=appScheme.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={if(vm.route.kind!in listOf("topic","editor","edit","login"))BottomBar(vm)},floatingActionButton={if(vm.route.kind in listOf("home","category"))FloatingActionButton(onClick={vm.open(Route("editor",cid=vm.route.cid))},containerColor=appScheme.primary,shape=RoundedCornerShape(20.dp)){Icon(AppIcons.Edit,"כתיבת דיון")}}){padding->
   Column(Modifier.fillMaxSize().padding(padding)){
    if(vm.route.kind !in listOf("home","topic"))Header(vm)
    if(vm.demo)Surface(color=Color(0xFFFFE0A0)){Text("מצב דמה מקומי · כל תקשורת הרשת חסומה",Modifier.fillMaxWidth().padding(9.dp),color=Color(0xFF634200),style=MaterialTheme.typography.labelMedium)}
    stateHolder.SaveableStateProvider(if(vm.route.kind=="topic")"topic-${vm.route.tid}"else vm.route.toString()){Box(Modifier.fillMaxSize().widthIn(max=960.dp).align(Alignment.CenterHorizontally)){when(vm.route.kind){
     "home","category","search","popular","unsolved","unread","tag"->FeedScreen(vm,onLink)
     "tags"->TagsScreen(vm)
     "categories"->CategoryScreen(vm)
     "topic"->ThreadScreen(vm,onLink,{url->link=safeLink(url)?.toString()})
     "saved"->SavedScreen(vm)
     "profile"->ProfileScreen(vm,onLink)
     "user"->UserProfileScreen(vm,onLink)
     "settings"->SettingsScreen(vm)
     "about"->AboutScreen(vm,onLink)
     "drafts"->DraftsScreen(vm)
     "history"->HistoryScreen(vm)
     "notifications"->NotificationsScreen(vm,onLink)
     "login"->LoginScreen(vm,onLink)
     "editor","edit"->EditorScreen(vm,onLink)
     "chats","chat"->ChatScreen(vm,onLink)
    }}}
   }
  }
  LaunchedEffect(link){link?.let{url->openBrowser(context,url,vm::showMessage);link=null}}
 }}
}
@Composable fun Header(vm:AppModel,inset:Boolean=true){val r=vm.route;val home=r.kind=="home";val title=when(r.kind){"categories"->"כל הקטגוריות";"category"->"בתוך הקהילה";"topic"->"דיון";"popular"->"דיונים פופולריים";"unsolved"->"ממתינים לפתרון";"unread"->"דיונים שלא נקראו";"tag"->"תגית: ${r.query}";"tags"->"תגיות";"saved"->"השמורים שלי";"profile"->"החשבון שלי";"user"->"פרופיל משתמש";"settings"->"הגדרות";"notifications"->"ההתראות שלי";"search"->"חיפוש בפורום";"login"->"ברוכים השבים";"edit"->"עריכת פוסט";"editor"->if(r.tid>0)"כתיבת תגובה"else "נושא חדש";"about"->"אודות";"drafts"->"טיוטות";"history"->"היסטוריית קריאה";"chats"->"הודעות פרטיות";"chat"->"שיחה פרטית";else->"מתמחים+"}
 if(vm.threadStyle=="chat"){ChatAppHeader(vm,title,inset);return}
 Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart=32.dp,bottomEnd=32.dp)).background(Brush.verticalGradient(listOf(Color(0xFF007F89),Color(0xFF235DA4)))).then(if(inset)Modifier.statusBarsPadding()else Modifier).padding(horizontal=18.dp,vertical=12.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){if(vm.canBack)IconButton(enabled=!vm.sending,onClick={vm.back()}){Icon(AppIcons.ArrowBack,"חזרה",tint=Color.White)};Text(title,color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(r.kind!in listOf("editor","edit","login")){IconButton(onClick={vm.open(Route("search"))}){Icon(AppIcons.Search,"חיפוש",tint=Color.White)};IconButton(onClick={vm.open(Route("notifications"))}){BadgedBox(badge={if(vm.unreadCount>0)Badge{Text(vm.unreadCount.toString())}}){Icon(AppIcons.NotificationsNone,"התראות",tint=Color.White)}}}}
  if(home){Row(Modifier.padding(top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("מתמחים טופ\nקהילת ידע וטכנולוגיה",color=Color.White,fontSize=29.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(9.dp));Surface(color=Color.White.copy(alpha=.17f),shape=CircleShape){Text("דיונים, מדריכים וסיוע הדדי",color=Color.White,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(horizontal=12.dp,vertical=7.dp))}};Box(Modifier.size(66.dp).clip(RoundedCornerShape(23.dp)).background(Color.White.copy(alpha=.17f)),contentAlignment=Alignment.Center){Icon(AppIcons.Forum,null,tint=Color.White,modifier=Modifier.size(34.dp))}}}
 }
}
@Composable fun BottomBar(vm:AppModel){NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp){listOf(Triple("home","בית",AppIcons.Home),Triple("categories","קטגוריות",AppIcons.GridView),Triple("saved","שמורים",AppIcons.BookmarkBorder),Triple("profile","חשבון",AppIcons.PersonOutline)).forEach { (kind,label,icon)->NavigationBarItem(selected=vm.route.kind==kind,onClick={vm.root(kind)},icon={Icon(icon,label)},label={Text(label,maxLines=1)},colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primary.copy(alpha=.12f)))}}}
@Composable fun Paper(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Card(modifier=modifier.fillMaxWidth(),shape=round,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(defaultElevation=2.dp)){Column(Modifier.padding(18.dp),content=content)}}
@Composable fun SectionTitle(title:String,action:String?=null,onClick:()->Unit={}){Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text(title,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));if(action!=null)FilledTonalButton(onClick=onClick){Text(action)}}}
@Composable fun Badge(text:String,color:Color=MaterialTheme.colorScheme.primary,modifier:Modifier=Modifier){Surface(modifier,color=color.copy(alpha=.12f),shape=CircleShape){Text(text,Modifier.padding(horizontal=10.dp,vertical=5.dp),color=color,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)}}
@Composable fun IconTile(icon:ImageVector,color:Color,size:Int=48){Box(Modifier.size(size.dp).clip(RoundedCornerShape(17.dp)).background(color),contentAlignment=Alignment.Center){Icon(icon,null,tint=Color.White,modifier=Modifier.size((size*.52).dp))}}
@Composable fun ErrorCard(message:String,retry:()->Unit){val secureError="חיבור מאובטח" in message;val networkError=listOf("חיבור","אינטרנט","שרת זמנית").any{it in message};Paper{Row(verticalAlignment=Alignment.Top){Icon(AppIcons.CloudOff,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=2.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(if(networkError)"לא הצלחנו להתחבר לפורום"else "לא ניתן לטעון את התוכן",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text(message,Modifier.padding(top=7.dp),style=MaterialTheme.typography.bodyMedium);if(networkError&&!secureError)Text("בדקו את חיבור האינטרנט ונסו שוב. אם שאר האתרים עובדים, ייתכן שהפורום אינו זמין זמנית.",Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Button(onClick=retry,modifier=Modifier.padding(top=10.dp)){Text(if(networkError)"בדיקה וניסיון חוזר"else "טעינה מחדש")}}}}}
@Composable fun EmptyCard(title:String,body:String,icon:ImageVector=AppIcons.Inbox){Paper{IconTile(icon,Teal);Spacer(Modifier.height(16.dp));Text(title,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=8.dp))}}
@Composable fun Skeletons(){val transition=rememberInfiniteTransition(label="loading");val alpha by transition.animateFloat(.25f,.7f,infiniteRepeatable(tween(850),RepeatMode.Reverse),label="pulse");val tint=MaterialTheme.colorScheme.onSurface.copy(alpha=alpha*.15f);Column(verticalArrangement=Arrangement.spacedBy(10.dp)){repeat(4){Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Box(Modifier.fillMaxWidth(.36f).height(12.dp).clip(CircleShape).background(tint));Spacer(Modifier.height(13.dp));Box(Modifier.fillMaxWidth(.88f).height(19.dp).clip(CircleShape).background(tint));Spacer(Modifier.height(7.dp));Box(Modifier.fillMaxWidth(.61f).height(19.dp).clip(CircleShape).background(tint));Spacer(Modifier.height(17.dp));Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(24.dp).clip(CircleShape).background(tint));Spacer(Modifier.width(8.dp));Box(Modifier.width(95.dp).height(12.dp).clip(CircleShape).background(tint))}}}}}}
@Composable fun TopicCard(t:Topic,vm:AppModel){
 if(vm.threadStyle=="chat"){ChatTopicRow(t,vm);return}
 val color=accents[Math.floorMod(t.cid,accents.size)]
 val read=vm.readIndex(t.id)
 val reading=read>0&&read<t.posts
 Card(onClick={vm.open(Route("topic",tid=t.id))},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.75f)),modifier=Modifier.fillMaxWidth()){
  Row(Modifier.fillMaxWidth()){
   Box(Modifier.width(4.dp).heightIn(min=140.dp).background(if(reading)MaterialTheme.colorScheme.primary else color.copy(alpha=.45f)))
   Column(Modifier.weight(1f).padding(start=15.dp,end=13.dp,top=12.dp,bottom=14.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){
    Text(t.category.ifBlank {"דיון בקהילה"},Modifier.weight(1f),color=color,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
    Spacer(Modifier.width(6.dp))
    if(t.solved)Badge("נפתר") else if(t.locked)Badge("נעול") else if(reading)Badge("להמשך קריאה")
    if(t.pinned)Icon(AppIcons.PushPin,"דיון נעוץ",Modifier.size(15.dp),tint=color)
    IconButton(onClick={vm.toggleSaved(t)},modifier=Modifier.size(38.dp)){Icon(if(vm.saved.any {it.id==t.id})AppIcons.Bookmark else AppIcons.BookmarkBorder,"שמירת דיון",tint=MaterialTheme.colorScheme.primary)}
   }
   Text(t.title,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium,lineHeight=25.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
   Spacer(Modifier.height(16.dp))
   Row(verticalAlignment=Alignment.CenterVertically){
    UserAvatar(t.avatar,t.author,26.dp,Modifier.clickable(enabled=t.authorSlug.isNotBlank()){vm.open(Route("user",query=t.authorSlug))},online=t.online,uid=t.authorUid)
    Spacer(Modifier.width(7.dp))
    Text(t.author,Modifier.weight(1f).clickable(enabled=t.authorSlug.isNotBlank()){vm.open(Route("user",query=t.authorSlug))},color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
    Icon(AppIcons.ChatBubbleOutline,null,Modifier.size(14.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    Text(" ${number((t.posts-1).coerceAtLeast(0))} ",style=MaterialTheme.typography.labelSmall)
    Spacer(Modifier.width(8.dp))
    Text(ago(t.time),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
   }
  }
 }
}
@Composable fun WatchCategoryButton(vm:AppModel,cid:Int,initial:Boolean){
 var watched by remember(cid,initial){mutableStateOf(initial)}
 var busy by remember(cid){mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 OutlinedButton(enabled=!busy,onClick={busy=true;scope.launch{try{vm.api.watchCategory(cid,watched);watched=!watched;vm.showMessage(if(watched)"המעקב אחר הקטגוריה הופעל"else "המעקב הופסק")}catch(e:Exception){vm.showMessage(friendlyError(e))}finally{busy=false}}}){Text(if(watched)"במעקב · הפסקה"else "מעקב אחר הקטגוריה")}
}
@Composable fun CategoryCard(c:Category,index:Int,vm:AppModel,modifier:Modifier=Modifier){val color=accents[index%accents.size];val icon=categoryIcon(c.name);Card(onClick={vm.openCategory(c)},modifier=modifier,shape=round,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.padding(18.dp).fillMaxWidth().heightIn(min=145.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){IconTile(icon,color);if(c.children.isNotEmpty())Badge("${c.children.size} תחומים",color)};Spacer(Modifier.height(22.dp));Text(c.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,maxLines=3,overflow=TextOverflow.Ellipsis);Spacer(Modifier.height(6.dp));Text("${number(c.count)} נושאים",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}}}
@Composable fun SubcategoryTile(c:Category,index:Int,vm:AppModel,modifier:Modifier=Modifier){val color=accents[Math.floorMod(index,accents.size)];val icon=categoryIcon(c.name);Card(onClick={vm.openCategory(c)},modifier=modifier,shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.fillMaxSize().padding(13.dp),verticalArrangement=Arrangement.SpaceBetween,horizontalAlignment=Alignment.CenterHorizontally){IconTile(icon,color,38);Text(c.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall,maxLines=3,overflow=TextOverflow.Ellipsis);Text("${number(c.count)} נושאים",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall,maxLines=1)}}}
@Composable fun CategorySkeletons(){val transition=rememberInfiniteTransition(label="category-loading");val alpha by transition.animateFloat(.22f,.62f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="category-pulse");Column(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.semantics{contentDescription="טוענים קטגוריות"}){repeat(3){Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){repeat(2){Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)){Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.SpaceBetween){Box(Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha=alpha*.18f)));Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.fillMaxWidth(.82f).height(13.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha=alpha*.16f)));Box(Modifier.fillMaxWidth(.54f).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha=alpha*.13f)))}}}}}}}}
@Composable fun Pager(page:Int,pages:Int,onPage:(Int)->Unit){var jump by remember{mutableStateOf(false)};var value by remember{mutableStateOf(page.toString())};if(pages>1){Paper{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){OutlinedButton(onClick={onPage(page-1)},enabled=page>1){Text("הקודם")};FilledTonalButton(onClick={value=page.toString();jump=true}){Text("$page / ${number(pages)}")};OutlinedButton(onClick={onPage(page+1)},enabled=page<pages){Text("הבא")}}};if(jump)AlertDialog(onDismissRequest={jump=false},title={Text("מעבר לעמוד")},text={OutlinedTextField(value=value,onValueChange={value=it.filter(Char::isDigit).take(6)},label={Text("עמוד 1–$pages")},singleLine=true)},confirmButton={Button(enabled=value.toIntOrNull() in 1..pages,onClick={jump=false;onPage(value.toInt())}){Text("מעבר")}},dismissButton={OutlinedButton(onClick={jump=false}){Text("ביטול")}})}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FeedScreen(vm:AppModel,onLink:(String)->Unit){if(vm.threadStyle=="chat"){ChatForumFeed(vm,onLink);return};val r=vm.route;val home=r.kind=="home";val listState=rememberLazyListState();PullToRefreshBox(isRefreshing=vm.refreshing,onRefresh=vm::retry){LazyColumn(state=listState,modifier=Modifier.testTag("home-feed"),contentPadding=if(home)PaddingValues(bottom=90.dp)else PaddingValues(18.dp,18.dp,18.dp,90.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 if(home)item(key="home-header"){Header(vm)}
 if(home)item(key="chat-invitation"){HomeFeedInset{ChatInvitation(vm)}}
 if(home&&vm.favoriteCategories.isNotEmpty())item{HomeFeedInset{Paper{Text("קטגוריות מועדפות",fontWeight=FontWeight.Bold);Row(Modifier.horizontalScroll(rememberScrollState())){((vm.directory as? Load.Ready)?.value?.let(::flatten).orEmpty()).filter{it.id in vm.favoriteCategories}.forEach{c->FilledTonalButton(onClick={vm.openCategory(c)}){Text(c.name)}}}}}}
 if(r.kind=="category")item{Column{FilledTonalButton(onClick={vm.toggleFavorite(r.cid)}){Text(if(r.cid in vm.favoriteCategories)"הסרה מהמועדפים"else "הוספה לקטגוריות מועדפות")};if(vm.account.uid>0)(vm.page as? Load.Ready)?.value?.let{WatchCategoryButton(vm,r.cid,it.watched)}}}
 if(home)item{HomeFeedInset{Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){QuickCard("כל הקטגוריות","מגלים תחומי עניין",AppIcons.GridView,Color(0xFF087363),Modifier.weight(1f)){vm.root("categories")};QuickCard("השמורים שלי","ממשיכים מהיכן שעצרנו",AppIcons.Bookmarks,Teal,Modifier.weight(1f)){vm.root("saved")}}}}
 if(home)item{HomeFeedInset{Column{Text("מגלים דיונים",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.padding(bottom=6.dp));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){(listOf(Triple("popular","פופולריים",AppIcons.AutoAwesome),Triple("unsolved","לא נפתר",AppIcons.Forum),Triple("tags","תגיות",AppIcons.GridView))+if(vm.account.uid>0)listOf(Triple("unread","לא נקראו",AppIcons.Inbox))else emptyList()).forEach{(kind,label,icon)->FilledTonalButton(onClick={vm.open(Route(kind))}){Icon(icon,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(label)}}}}}}
 if(r.kind=="search")item{SearchPanel(vm)}
 item{HomeFeedInset(home){SectionTitle(if(r.kind=="home")"מה חדש בקהילה" else if(r.kind=="search")"תוצאות חיפוש" else (vm.page as? Load.Ready)?.value?.name?:"דיונים", "רענון"){vm.retry()}}}
 when(val data=vm.page){Load.Waiting->item{HomeFeedInset(home){if(r.kind=="category")CategorySkeletons()else Skeletons()}};is Load.Failed->item{HomeFeedInset(home){Column{ErrorCard(data.message){vm.retry()};if(vm.account.uid==0)OutlinedButton(onClick={vm.open(Route("login"))}){Text("כניסה לחשבון")}}}};is Load.Ready->{
  if(data.value.children.isNotEmpty()){
   item{SectionTitle("תתי־קטגוריות")}
   items(data.value.children.chunked(2),key={row->"subcategory-row-${row.first().id}"}){row->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
    row.forEachIndexed{index,c->SubcategoryTile(c,index+c.id,vm,Modifier.weight(1f).aspectRatio(1f))}
    if(row.size==1)Spacer(Modifier.weight(1f))
   }}
  }
  if(data.value.link.isNotEmpty())item{Paper{Text("זו קטגוריית קישור בפורום.");OutlinedButton(onClick={onLink(data.value.link)}){Text("פתיחת הקישור")}}}
  if(data.value.topics.isEmpty()&&data.value.link.isEmpty())item{HomeFeedInset(home){EmptyCard("אין דיונים להצגה בעמוד הזה","אפשר לבחור תת־קטגוריה או לחזור לעמוד קודם.")}}
  items(data.value.topics,key={it.id}){topic->HomeFeedInset(home){TopicCard(topic,vm)}}
  item{HomeFeedInset(home){Pager(data.value.page,data.value.pages){vm.open(r.copy(page=it))}}}
 }}
 if(home)item{HomeFeedInset{Text("מתמחים+ · קהילת ידע וטכנולוגיה",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
}}}
@Composable fun HomeFeedInset(enabled:Boolean=true,content:@Composable ()->Unit){if(enabled)Box(Modifier.fillMaxWidth().padding(horizontal=18.dp)){content()}else content()}
@Composable fun QuickCard(title:String,subtitle:String,icon:ImageVector,color:Color,modifier:Modifier,onClick:()->Unit){Card(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.75f))){Column(Modifier.padding(17.dp)){IconTile(icon,color);Spacer(Modifier.height(17.dp));Text(title,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium);Text(subtitle,Modifier.padding(top=5.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
@Composable fun CategoryScreen(vm:AppModel){if(vm.threadStyle=="chat"){ChatCategories(vm);return};var query by rememberSaveable{mutableStateOf("")};LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){item{OutlinedTextField(value=query,onValueChange={query=it},placeholder={Text("איזה תחום מעניין אותך?" )},leadingIcon={Icon(AppIcons.Search,null)},shape=RoundedCornerShape(20.dp),singleLine=true,modifier=Modifier.fillMaxWidth())};when(val data=vm.directory){Load.Waiting->item{CategorySkeletons()};is Load.Failed->item{ErrorCard(data.message){vm.reloadDirectory()}};is Load.Ready->{val all=flatten(data.value);val rows=(if(query.isBlank())data.value else all.filter {it.name.contains(query,true)}).chunked(2);item{SectionTitle(if(query.isBlank())"${all.size} קטגוריות ותתי־קטגוריות" else "תוצאות בכל הקטגוריות")};if(rows.isEmpty())item{EmptyCard("לא נמצאה קטגוריה","נסו מילה אחרת.")};items(rows){row->Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){row.forEach {c->CategoryCard(c,all.indexOf(c).coerceAtLeast(0),vm,Modifier.weight(1f))};if(row.size==1)Spacer(Modifier.weight(1f))}}}}}}
@Composable fun SavedScreen(vm:AppModel){
 var query by rememberSaveable{mutableStateOf("")}
 var mode by rememberSaveable{mutableStateOf("all")}
 var remotePage by rememberSaveable{mutableIntStateOf(1)}
 var revision by remember{mutableIntStateOf(0)}
 var remote by remember{mutableStateOf<Load<ProfileSection>>(Load.Waiting)}
 val scope=rememberCoroutineScope()
 LaunchedEffect(mode,remotePage,revision,vm.account.uid){if(mode!="local"&&vm.account.uid>0&&vm.account.slug.isNotBlank()){
  remote=Load.Waiting
  remote=try{Load.Ready(vm.api.profileSection(vm.account.slug,"bookmarks",remotePage))}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){Load.Failed(friendlyError(e))}
 }}
 val selected=vm.saved.filter{it.title.contains(query,true)||it.category.contains(query,true)}
 LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=mode=="all",onClick={mode="all"},label={Text("הכול")});FilterChip(selected=mode=="local",onClick={mode="local"},label={Text("במכשיר")});FilterChip(selected=mode=="forum",onClick={mode="forum"},label={Text("בחשבון")})}}
  if(mode!="forum"){
   item{OutlinedTextField(value=query,onValueChange={query=it},label={Text("חיפוש בשמורים")},modifier=Modifier.fillMaxWidth());SectionTitle("${vm.saved.size} דיונים ששמרת")}
   if(selected.isEmpty())item{EmptyCard(if(query.isBlank())"שומרים להמשך"else "לא נמצאו דיונים","לחיצה על הסימנייה ליד דיון תשמור אותו במכשיר.",AppIcons.BookmarkBorder)}
   items(selected,key={"local-${it.id}"}){savedTopic->
    var busy by remember(savedTopic.id,vm.account.uid){mutableStateOf(false)}
    var transferred by remember(savedTopic.id,vm.account.uid){mutableStateOf(false)}
    Column{TopicCard(savedTopic,vm)
     if(vm.account.uid>0)OutlinedButton(enabled=!busy&&!transferred,onClick={busy=true;scope.launch{try{vm.api.bookmarkTopicMainPost(savedTopic.id);transferred=true;revision++;vm.showMessage("הדיון נשמר גם בחשבון; השמירה במכשיר נשארה") }catch(e:Exception){vm.showMessage(friendlyError(e))}finally{busy=false}}},modifier=Modifier.padding(top=5.dp)){Text(if(busy)"שומר בחשבון…"else if(transferred)"נשמר גם בחשבון"else "שמירה גם בחשבון")}
    }
   }
  }
  if(mode!="local"&&vm.account.uid<=0){
   if(mode=="forum")item{EmptyCard("סימניות החשבון","היכנסו לחשבון הפורום כדי לראות פוסטים ששמרתם באתר.");Button(onClick={vm.open(Route("login"))}){Text("כניסה לחשבון")}}
  }else if(mode!="local"&&vm.account.slug.isNotBlank()){
   item{SectionTitle("סימניות החשבון");Text("אלה פוסטים ששמרת בפורום. השמורים במכשיר נשארים ללא שינוי.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   when(val data=remote){
    Load.Waiting->item{Skeletons()}
    is Load.Failed->item{ErrorCard(data.message){revision++}}
    is Load.Ready->{val posts=data.value.posts
     if(posts.isEmpty())item{EmptyCard("אין סימניות להצגה","אפשר לשמור פוסט מתוך תפריט הפעולות שלו בדיון.")}
     items(posts,key={"forum-${it.pid}"}){post->Card(onClick={vm.openPost(post.pid)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(16.dp)){Text(post.title,fontWeight=FontWeight.SemiBold);if(post.excerpt.isNotBlank())Text(post.excerpt,Modifier.padding(top=7.dp),maxLines=3,style=MaterialTheme.typography.bodyMedium)}}}
     item{Pager(data.value.page,data.value.pages){remotePage=it}}
    }
   }
  }
 }
}
