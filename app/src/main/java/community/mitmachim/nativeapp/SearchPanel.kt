package community.mitmachim.nativeapp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun SearchPanel(vm:AppModel) {
 val route=vm.route
 var term by remember(route.query){mutableStateOf(route.query)}
 var by by remember(route.by){mutableStateOf(route.by)}
 var cid by remember(route.cid){mutableIntStateOf(route.cid)}
 var range by remember(route.range){mutableIntStateOf(route.range)}
 var searchIn by remember(route.searchIn){mutableStateOf(route.searchIn)}
 var choosing by remember{mutableStateOf(false)}
 var categoryQuery by remember{mutableStateOf("")}
 val categories=(vm.directory as? Load.Ready)?.value?.let(::flatten).orEmpty().filter{it.id>0&&it.link.isBlank()}
 Paper {
  OutlinedTextField(value=term,onValueChange={term=it},label={Text("מה מחפשים בפורום?")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Spacer(Modifier.height(10.dp))
  OutlinedTextField(value=by,onValueChange={by=it},label={Text("שם כותב — לא חובה")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Spacer(Modifier.height(10.dp))
  OutlinedButton(onClick={choosing=true}){Text(categories.firstOrNull{it.id==cid}?.name?:"כל הקטגוריות")}
  Text("חיפוש בתוך",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=8.dp))
  Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
   listOf("titlesposts" to "כותרות ותוכן","titles" to "כותרות","posts" to "תוכן פוסטים").forEach{(id,label)->FilterChip(selected=searchIn==id,onClick={searchIn=id},label={Text(label)})}
  }
  Text("פורסם ב־",style=MaterialTheme.typography.labelLarge)
  Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
   listOf(0 to "כל הזמנים",7 to "שבוע",30 to "חודש",365 to "שנה").forEach{(days,label)->FilterChip(selected=range==days,onClick={range=days},label={Text(label)})}
  }
  Button(onClick={vm.open(Route("search",cid=cid,query=term.trim(),by=by.trim(),range=range,searchIn=searchIn))},enabled=term.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("חיפוש") }
  if(vm.account.uid==0)Text("הפורום מאפשר חיפוש מלא לאחר כניסה לחשבון.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
 }
 if(choosing)AlertDialog(onDismissRequest={choosing=false},title={Text("בחירת קטגוריה")},text={
  Column {
   OutlinedTextField(value=categoryQuery,onValueChange={categoryQuery=it},label={Text("חיפוש קטגוריה")},singleLine=true)
   Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState())) {
    listOf(Category(0,0,"כל הקטגוריות",0)).plus(categories.filter{it.name.contains(categoryQuery,true)}).forEach{category->
     FilledTonalButton(onClick={cid=category.id;choosing=false},modifier=Modifier.fillMaxWidth()) { Text(category.name) }
    }
   }
  }
 },confirmButton={OutlinedButton(onClick={choosing=false}){Text("סגירה")}})
}
