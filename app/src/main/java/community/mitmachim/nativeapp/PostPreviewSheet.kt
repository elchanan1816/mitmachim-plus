package community.mitmachim.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Local rendering only. Opening a preview never uploads attachments or submits content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PostPreviewSheet(vm:AppModel,source:String,title:String="",replyName:String="",files:List<LocalFile> = emptyList(),onDismiss:()->Unit){
 val html=remember(source){markdownToHtml(source)}
 ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
  Column(Modifier.fillMaxWidth().heightIn(max=640.dp).verticalScroll(rememberScrollState()).padding(18.dp).testTag("post-preview"),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("תצוגה לפני שליחה",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
   Text("זוהי תצוגה מקומית. הפורום עשוי להציג עיצוב מעט שונה.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   if(title.isNotBlank())Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
   if(replyName.isNotBlank())Text("בתגובה ל־$replyName",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
   Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=MaterialTheme.shapes.large){Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    if(source.isBlank())Text("עדיין לא נכתב תוכן.",color=MaterialTheme.colorScheme.onSurfaceVariant)
    else RichContent(html,vm,{vm.showMessage("זהו קישור בתצוגת הפוסט. הטיוטה לא נשלחה.")},compact=vm.threadStyle=="chat")
    files.filter{it.uploadedUrl==null}.forEach{file->
     if(file.type.startsWith("image/"))AsyncImage(model=file.uri,contentDescription=file.name,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(200.dp))
     Text("קובץ מצורף: ${file.name} · טרם הועלה",style=MaterialTheme.typography.bodySmall)
    }
   }}
   FilledTonalButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()){Text("חזרה לכתיבה")}
  }
 }
}
