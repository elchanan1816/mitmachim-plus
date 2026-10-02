package community.mitmachim.nativeapp

import android.graphics.Color as AndroidColor
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.text.TextPaint
import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.content.res.ResourcesCompat
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

data class PostContent(val html:String,val images:List<Pair<String,String>>,val code:List<String>,val tables:List<List<List<String>>>,val details:List<Pair<String,String>>,val media:List<String>,val attachments:List<Pair<String,String>> = emptyList())
fun isAttachmentLink(value:String):Boolean=safeLink(value)?.encodedPath?.substringAfterLast('.')?.lowercase() in setOf("pdf","zip","apk","doc","docx","xls","xlsx","txt","rar","7z")
/** Convert NodeBB's spoiler-plugin markup into the expandable block already used by the app. */
fun normalizeSpoilerMarkup(html:String,markdownSyntax:Boolean=false):String {
 val doc=Jsoup.parseBodyFragment(html,FORUM)
 fun replace(block:org.jsoup.nodes.Element,label:String,body:String){
  val details=org.jsoup.nodes.Element("details")
  details.appendElement("summary").text(label.ifBlank { "הצגת ספוילר" })
  details.append(body)
  block.replaceWith(details)
 }
 doc.select("blockquote.spoiler").forEach { block->
  val label=block.selectFirst("button")?.text()?.trim().orEmpty()
  val body=block.select("p").joinToString("<br>"){it.html()}.ifBlank { block.html() }
  replace(block,label,body)
 }
 if(markdownSyntax)doc.select("blockquote:not(.spoiler)").forEach { block->
  val first=block.selectFirst("p")?:return@forEach
  val firstHtml=first.html()
  if(!firstHtml.startsWith("! "))return@forEach
  val content=buildList {
   add(firstHtml.removePrefix("! "))
   block.select("p").drop(1).forEach{add(it.html())}
  }.joinToString("<br>")
  replace(block,"הצגת ספוילר",content)
 }
 return doc.body().html()
}
fun splitContent(html:String):PostContent {
 val doc=Jsoup.parseBodyFragment(normalizeSpoilerMarkup(html),FORUM);doc.select("script,style,iframe,object,embed,form,input,button,svg,math").remove()
 val details=doc.select("details").map { e->val summary=e.selectFirst("summary")?.text()?:"הצגת תוכן נוסף";e.select("summary").remove();val content=e.html();e.remove();summary to content }
 val images=doc.select("img").mapNotNull {e->val src=safeLink(e.attr("src"))?.toString();val alt=e.attr("alt");if(e.hasClass("emoji")){e.after(org.jsoup.nodes.TextNode(alt));e.remove();null}else{e.remove();src?.let {it to alt}}}
 val code=doc.select("pre").map {e->val value=e.wholeText();e.remove();value}
 val tables=doc.select("table").map {e->val rows=e.select("tr").map {r->r.select("th,td").map {it.text()}};e.remove();rows}
 val media=doc.select("video,audio,source").mapNotNull {safeLink(it.attr("src"))?.toString()}.distinct();doc.select("video,audio,source").remove()
 val attachments=doc.select("a[href]").mapNotNull{e->if(isAttachmentLink(e.attr("href"))){val url=safeLink(e.attr("href"))?.toString();val label=e.text().ifBlank{url?.substringAfterLast('/')?:"קובץ מצורף"};e.remove();url?.let{it to label}}else null}
 return PostContent(Jsoup.clean(doc.body().html(),FORUM,Safelist.relaxed().removeTags("img").addTags("del","s")),images,code,tables,details,media,attachments)
}
@Composable fun NativeHtml(html:String,onLink:(String)->Unit,compact:Boolean=false){
 val color=MaterialTheme.colorScheme.onSurface.toArgb()
 val linkColor=MaterialTheme.colorScheme.primary.toArgb()
 val density=androidx.compose.ui.platform.LocalDensity.current
 val discussionSans=LocalDiscussionSans.current
 val text=remember(html,onLink,linkColor){
  val s=SpannableStringBuilder(HtmlCompat.fromHtml(html,HtmlCompat.FROM_HTML_MODE_COMPACT))
  s.getSpans(0,s.length,URLSpan::class.java).forEach {span->
   val a=s.getSpanStart(span);val b=s.getSpanEnd(span);s.removeSpan(span)
   if(safeLink(span.url)!=null)s.setSpan(object:ClickableSpan(){
    override fun onClick(widget:View){onLink(span.url)}
    override fun updateDrawState(ds:TextPaint){super.updateDrawState(ds);ds.color=linkColor;ds.isUnderlineText=false;ds.isFakeBoldText=true}
   },a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
  };s.trim()
 }
 AndroidView(modifier=if(compact)Modifier.wrapContentWidth()else Modifier.fillMaxWidth(),factory={context->TextView(context).apply {
  typeface=ResourcesCompat.getFont(context,R.font.heebo)
  setTextIsSelectable(true);movementMethod=LinkMovementMethod.getInstance();layoutDirection=View.LAYOUT_DIRECTION_RTL
  textDirection=View.TEXT_DIRECTION_FIRST_STRONG;setLineSpacing(0f,1.35f);setPadding(0,0,0,0)
 }},update={it.text=text;it.typeface=if(richTextUsesSystemFont(compact,discussionSans))android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL)else ResourcesCompat.getFont(it.context,R.font.heebo);it.includeFontPadding=!richTextUsesSystemFont(compact,discussionSans);it.setTextColor(color);it.setLinkTextColor(linkColor);it.setLineSpacing(0f,richTextLineSpacing(compact,discussionSans));it.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(if(compact)16 else 17)*density.density*density.fontScale)})
}
/** Keep text, images, code and tables in document order, including nested containers. */
fun orderedPostFragments(html:String):List<String> {
 val doc=Jsoup.parseBodyFragment(normalizeSpoilerMarkup(html),FORUM)
 doc.select("script,style,iframe,object,embed,form,input,button,svg,math").remove()
 val selector="img:not(.emoji),pre,table,details,video,audio"
 val result=mutableListOf<String>();val pending=StringBuilder()
 fun flush(){if(pending.isNotBlank())result.add(pending.toString());pending.setLength(0)}
 fun visit(node:org.jsoup.nodes.Node){
  if(node is org.jsoup.nodes.Element && (node.`is`(selector)||node.tagName()=="a"&&isAttachmentLink(node.attr("href")))){flush();result.add(node.outerHtml())}
  else if(node is org.jsoup.nodes.Element && (node.select(selector).isNotEmpty()||node.select("a[href]").any{isAttachmentLink(it.attr("href"))})){
   if(node.isBlock)flush()
   node.childNodes().forEach(::visit)
   if(node.isBlock)flush()
  }else pending.append(node.outerHtml())
 }
 doc.body().childNodes().forEach(::visit);flush();return result
}
@Composable fun RichContent(html:String,vm:AppModel,onLink:(String)->Unit,compact:Boolean=false){val fragments=remember(html){orderedPostFragments(html)};Column(verticalArrangement=Arrangement.spacedBy(if(compact)6.dp else 12.dp)){fragments.forEach{RichFragment(it,vm,onLink,compact)}}}
@Composable private fun RichFragment(html:String,vm:AppModel,onLink:(String)->Unit,compact:Boolean=false){val content=remember(html){splitContent(html)};Column(verticalArrangement=Arrangement.spacedBy(if(compact)6.dp else 12.dp)){
 if(content.html.isNotBlank())NativeHtml(content.html,onLink,compact)
 content.code.forEach {code->Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(14.dp)){Text(code,style=MaterialTheme.typography.bodySmall.copy(textDirection=androidx.compose.ui.text.style.TextDirection.Ltr),fontFamily=FontFamily.Monospace,modifier=Modifier.fillMaxWidth().padding(13.dp).horizontalScroll(rememberScrollState()))}}
 content.tables.forEach {rows->Column(Modifier.horizontalScroll(rememberScrollState()).border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(10.dp))){rows.forEachIndexed {index,row->Row{row.forEach {cell->Text(cell,Modifier.width(160.dp).background(if(index==0)MaterialTheme.colorScheme.surfaceContainer else Color.Transparent).padding(12.dp),fontWeight=if(index==0)FontWeight.Bold else FontWeight.Normal,style=MaterialTheme.typography.bodySmall)}}}}}
 content.images.forEach {(url,alt)->ForumImage(url,alt,vm)}
 content.attachments.forEach {(url,label)->Card(onClick={onLink(url)},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(AppIcons.AttachFile,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp));Column{Text(label,fontWeight=FontWeight.SemiBold);Text("פתיחת הקובץ",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
 content.media.forEach {url->OutlinedButton(onClick={onLink(url)}){Icon(AppIcons.PlayCircleOutline,null);Text(" פתיחת מדיה בנגן חיצוני")}}
 content.details.forEach {(summary,body)->var expanded by rememberSaveable(body){mutableStateOf(false)};OutlinedCard{OutlinedButton(onClick={expanded=!expanded},modifier=Modifier.padding(8.dp)){Icon(if(expanded)AppIcons.ExpandLess else AppIcons.ExpandMore,null);Text(summary)};if(expanded)Box(Modifier.padding(12.dp)){RichContent(body,vm,onLink,compact)}}}
 }}

@Composable fun ProfileScreen(vm:AppModel,onLink:(String)->Unit){
 LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  item{Paper{Row(verticalAlignment=Alignment.CenterVertically){UserAvatar(vm.account.avatar,if(vm.account.uid>0)vm.account.name else "אורח",60.dp,online=vm.account.online,uid=vm.account.uid);Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(if(vm.account.uid>0)vm.account.name else "ברוכים הבאים",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.headlineSmall);Text(if(vm.account.uid>0)"מחובר למתמחים טופ"else "היכנסו כדי לפרסם ולהשתתף בדיונים",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}};if(vm.account.uid==0)Button(onClick={vm.open(Route("login"))},modifier=Modifier.fillMaxWidth().padding(top=16.dp)){Text("כניסה לחשבון")}}}
  item{Paper{if(vm.account.uid>0&&vm.account.slug.isNotBlank())MenuRow("הפרופיל הציבורי שלי","נושאים, פוסטים ועוקבים",AppIcons.PersonOutline){vm.open(Route("user",query=vm.account.slug))};MenuRow("שמורים","דיונים במכשיר וסימניות הפורום",AppIcons.BookmarkBorder){vm.root("saved")};MenuRow("היסטוריית קריאה","חזרה לדיונים שקראת",AppIcons.Forum){vm.open(Route("history"))};MenuRow("טיוטות","המשך כתיבה מהמקום שבו הפסקת",AppIcons.Edit){vm.open(Route("drafts"))};MenuRow("התראות",if(vm.unreadCount>0)"${vm.unreadCount} התראות שלא נקראו"else "עדכוני החשבון",AppIcons.NotificationsNone){vm.open(Route("notifications"))};MenuRow("הודעות פרטיות","השיחות שלך בפורום",AppIcons.Forum){vm.open(Route("chats"))};MenuRow("הגדרות","מראה, קריאות והתראות",AppIcons.Tune){vm.open(Route("settings"))};MenuRow("אודות","על האפליקציה ועל מפתחיה",AppIcons.Groups){vm.open(Route("about"))}}}
  item{Paper{Text("כל אפשרויות הפורום",fontWeight=FontWeight.Bold);Text("אפשר לפתוח את האתר גם מתוך האפליקציה. התחברות דרך Google זמינה במצב אתר; החיבור במסכי האפליקציה נעשה בנפרד.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=8.dp));OutlinedButton(onClick={onLink("$FORUM/me")}){Text("פתיחת החשבון באתר")}}}
  if(vm.account.uid>0)item{var confirm by remember{mutableStateOf(false)};OutlinedButton(onClick={confirm=true},modifier=Modifier.fillMaxWidth()){Text("יציאה מהחשבון")};if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("לצאת מהחשבון במכשיר?")},confirmButton={Button(onClick={confirm=false;vm.logout()}){Text("יציאה")}},dismissButton={OutlinedButton(onClick={confirm=false}){Text("ביטול")}})}
 }
}
@Composable fun MenuRow(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)};Icon(AppIcons.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable fun SettingsScreen(vm:AppModel){
 LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  item{Paper{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("תצוגת צ׳אט",fontWeight=FontWeight.SemiBold);Text("מראה בסגנון ווטסאפ לבית, לקטגוריות ולדיונים",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked=vm.threadStyle=="chat",onCheckedChange={vm.chooseThreadStyle(if(it)"chat"else "discussion")},modifier=Modifier.semantics{contentDescription="הפעלת תצוגת צ׳אט"})}}}
  item{Paper{Text("מראה האפליקציה",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);listOf("system" to "לפי המכשיר","light" to "מצב יום","dark" to "מצב לילה").forEach{(value,label)->Row(Modifier.fillMaxWidth().clickable{vm.chooseTheme(value)},verticalAlignment=Alignment.CenterVertically){RadioButton(selected=vm.theme==value,onClick={vm.chooseTheme(value)});Text(label)}};HorizontalDivider(Modifier.padding(vertical=10.dp));Text("גודל כתב · ${(vm.fontScale*100).toInt()}%");Slider(value=vm.fontScale,onValueChange=vm::chooseFontScale,valueRange=.85f..1.5f,steps=12);Text("כך ייראה הטקסט בדיונים ובמסכי האפליקציה",style=MaterialTheme.typography.bodyMedium)}}
  item{BackgroundAlertsSettings(vm)}
  item{AppUpdatePanel(vm)}
  item{Paper{Text("תקשורת ופרטיות",fontWeight=FontWeight.Bold);Text("נתוני החשבון נשלחים לפורום. תמונות ותוכן חיצוני נטענים אוטומטית מהכתובות המופיעות בפוסטים. פרטי ההתחברות אינם נשלחים למארחי התמונות. הסיסמה אינה נשמרת במכשיר.",Modifier.padding(vertical=10.dp),style=MaterialTheme.typography.bodyMedium)}}
  item{Paper{MenuRow("אודות","גרסה, פיתוח ופרטי האפליקציה",AppIcons.Groups){vm.open(Route("about"))}}}
 }
}
@Composable fun LoginScreen(vm:AppModel,onLink:(String)->Unit){
 var name by rememberSaveable{mutableStateOf("")};var password by remember{mutableStateOf("")};var visible by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};val scope=rememberCoroutineScope();val context=LocalContext.current
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Paper{IconTile(AppIcons.LockOpen,Teal,60);Spacer(Modifier.height(18.dp));Text("כניסה למתמחים טופ",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge);Text("השתמשו בשם המשתמש ובסיסמת הפורום.",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodyMedium)
   OutlinedTextField(value=name,onValueChange={name=it},label={Text("שם משתמש או אימייל")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy);Spacer(Modifier.height(12.dp))
   OutlinedTextField(value=password,onValueChange={password=it},label={Text("סיסמה")},singleLine=true,visualTransformation=if(visible)androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={visible=!visible}){Icon(AppIcons.Visibility,if(visible)"הסתרת הסיסמה"else "הצגת הסיסמה")}},modifier=Modifier.fillMaxWidth(),enabled=!busy)
   Spacer(Modifier.height(18.dp));Button(onClick={busy=true;scope.launch{try{error=vm.login(name.trim(),password)}finally{password="";busy=false}}},enabled=!busy&&name.isNotBlank()&&password.isNotEmpty(),modifier=Modifier.fillMaxWidth()){if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Text("כניסה לחשבון")}
   error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=10.dp))};OutlinedButton(onClick={onLink("$FORUM/reset")}){Text("שכחתי את הסיסמה")}
  }
  Paper{Text("כניסה באמצעות Google",fontWeight=FontWeight.Bold);Text("הכניסה נפתחת בחלון האתר. לאחר ההתחברות ניתן להשתמש שם בכל אפשרויות הפורום. החיבור אינו מועבר למסכי האפליקציה.",style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(vertical=10.dp));OutlinedButton(onClick={openBrowser(context,"$FORUM/auth/google",vm::showMessage)},modifier=Modifier.fillMaxWidth()){Text("המשך עם Google באתר")}}
  OutlinedButton(onClick={onLink("$FORUM/register")},modifier=Modifier.fillMaxWidth()){Text("הרשמה לפורום")}
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NotificationsScreen(vm:AppModel,onLink:(String)->Unit){
 androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing=vm.refreshing,onRefresh=vm::retry){
 LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{SectionTitle("התראות","רענון"){vm.refreshNotices()}}
  if(vm.account.uid==0)item{EmptyCard("התראות אישיות","היכנסו לחשבון כדי לראות את העדכונים שלכם.",AppIcons.NotificationsNone);Button(onClick={vm.open(Route("login"))}){Text("כניסה לחשבון")}}
  else when(val data=vm.notices){
   Load.Waiting->item{Skeletons()};is Load.Failed->item{ErrorCard(data.message){vm.refreshNotices()}}
   is Load.Ready->{if(data.value.isEmpty())item{EmptyCard("הכול מעודכן","אין התראות להצגה.")};items(data.value,key={it.id.ifBlank{it.path+it.time+it.text}}){notice->Paper{if(!notice.read)Badge("חדש");Text(notice.text,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.padding(top=6.dp));Text(ago(notice.time),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(notice.path.isNotBlank())FilledTonalButton(onClick={vm.readNotice(notice);onLink(notice.path)}){Text("פתיחה בהקשר")};if(!notice.read)OutlinedButton(onClick={vm.readNotice(notice)}){Text("סימון כנקראה")}}}}}
  }
 }}
}
