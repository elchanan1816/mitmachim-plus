package community.mitmachim.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable fun TagsScreen(vm:AppModel) {
 var query by remember { mutableStateOf("") }
 var revision by remember { mutableIntStateOf(0) }
 var data by remember { mutableStateOf<Load<List<ForumTag>>>(Load.Waiting) }
 LaunchedEffect(revision) {
  data=Load.Waiting
  data=try { Load.Ready(vm.api.tags()) } catch(e:Exception) { Load.Failed(friendlyError(e)) }
 }
 val results=(data as? Load.Ready)?.value.orEmpty().filter{it.name.contains(query,true)}
 LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item { OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),label={Text("חיפוש תגית")},singleLine=true) }
  when(val current=data) {
   Load.Waiting->item { Skeletons() }
   is Load.Failed->item { ErrorCard(current.message){revision++} }
   is Load.Ready-> {
    item { Text("תגיות מובילות בפורום",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold) }
    if(results.isEmpty())item { EmptyCard("לא נמצאה תגית ברשימה","אפשר לנסות לפתוח תגית בשם שחיפשתם.");if(query.isNotBlank())OutlinedButton(onClick={vm.open(Route("tag",query=query.trim()))}){Text("פתיחת #${query.trim()}")} }
    items(results,key={it.name}) { tag ->
     Card(onClick={vm.open(Route("tag",query=tag.name))},modifier=Modifier.fillMaxWidth()) {
      Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
       Text("#${tag.name}",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
       Text("${number(tag.count)} דיונים",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium)
      }
     }
    }
   }
  }
 }
}
